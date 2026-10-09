"""Regression tests for the Maven Central release counter; no network required."""

import contextlib
import importlib.util
import io
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
from unittest.mock import patch
from urllib.error import HTTPError, URLError


SPEC = importlib.util.spec_from_file_location("next_version", Path(__file__).with_name("next-version.py"))
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


def metadata(*versions, group="net.aechronis", artifact="luckperms-minestom"):
    root = ET.Element("metadata")
    ET.SubElement(root, "groupId").text = group
    ET.SubElement(root, "artifactId").text = artifact
    entries = ET.SubElement(ET.SubElement(root, "versioning"), "versions")
    for version in versions:
        ET.SubElement(entries, "version").text = version
    return ET.tostring(root)


class NextVersionTest(unittest.TestCase):
    def test_first_release_starts_at_one(self):
        self.assertEqual(release.next_version("5.5.87", []), "5.5.87-minestom.1")

    def test_counter_uses_numeric_maximum(self):
        self.assertEqual(
            release.next_version("5.5.87", ["5.5.87-minestom.9", "5.5.87-minestom.12", "5.5.87-minestom.2"]),
            "5.5.87-minestom.13",
        )

    def test_new_base_resets_counter(self):
        self.assertEqual(
            release.next_version("5.5.88", ["5.5.87-minestom.42", "5.5.89-minestom.7"]),
            "5.5.88-minestom.1",
        )

    def test_only_exact_release_versions_count(self):
        self.assertEqual(
            release.next_version("5.5.87", [
                "5x5x87-minestom.99", "5.5.870-minestom.99", "v5.5.87-minestom.99",
                "5.5.87-minestom.99-SNAPSHOT", "5.5.87-minestom-SNAPSHOT", "5.5.87",
                "5.5.87-minestom.2",
            ]),
            "5.5.87-minestom.3",
        )


class BaseVersionTest(unittest.TestCase):
    def read_base(self, gradle):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "minestom").mkdir()
            (root / "minestom/build.gradle").write_text(gradle)
            with patch.object(release, "ROOT", root):
                return release.luckperms_version()

    def test_reads_pinned_base_not_generated_build_version(self):
        self.assertEqual(self.read_base(
            "group = 'net.aechronis'\ndef baseVersion = '5.5.87'\n"
            'version = "${baseVersion}-minestom-SNAPSHOT"\n'
        ), "5.5.87")

    def test_new_upstream_base_is_read(self):
        self.assertEqual(self.read_base("def baseVersion = '5.6.3'\n"), "5.6.3")

    def test_missing_or_non_release_base_fails(self):
        for gradle in ["", "// def baseVersion = '5.5.87'", "def baseVersion = '5.5'",
                       "def baseVersion = '5.5.87-SNAPSHOT'", "def baseVersion = project.version"]:
            with self.subTest(gradle=gradle), self.assertRaises(ValueError):
                self.read_base(gradle)


class MetadataTest(unittest.TestCase):
    def test_reads_versions_and_requests_uncached_metadata(self):
        with patch.object(release, "urlopen", return_value=io.BytesIO(metadata("5.5.87-minestom.1", " 5.5.87-minestom.2 "))) as fetch:
            self.assertEqual(release.published_versions(), ["5.5.87-minestom.1", "5.5.87-minestom.2"])
        request = fetch.call_args.args[0]
        self.assertEqual(request.full_url, release.METADATA_URL)
        self.assertEqual(request.get_header("Cache-control"), "no-cache")
        self.assertEqual(fetch.call_args.kwargs, {"timeout": 30})

    def test_only_404_means_no_releases(self):
        error = HTTPError(release.METADATA_URL, 404, "Not Found", None, None)
        self.addCleanup(error.close)
        with patch.object(release, "urlopen", side_effect=error):
            self.assertEqual(release.published_versions(), [])

    def test_other_http_errors_propagate(self):
        for status in [401, 403, 429, 500, 503]:
            error = HTTPError(release.METADATA_URL, status, "Unavailable", None, None)
            self.addCleanup(error.close)
            with self.subTest(status=status), patch.object(release, "urlopen", side_effect=error):
                with self.assertRaises(HTTPError):
                    release.published_versions()

    def test_network_failures_propagate(self):
        for error in [URLError("DNS failed"), TimeoutError("timed out")]:
            with self.subTest(error=error), patch.object(release, "urlopen", side_effect=error):
                with self.assertRaises(type(error)):
                    release.published_versions()

    def test_malformed_xml_fails(self):
        with patch.object(release, "urlopen", return_value=io.BytesIO(b"<metadata>")):
            with self.assertRaises(ET.ParseError):
                release.published_versions()

    def test_wrong_or_missing_coordinates_fail(self):
        for document in [b"<html/>", b"<metadata/>", metadata("5.5.87-minestom.1", group="other"),
                         metadata("5.5.87-minestom.1", artifact="grim-minestom")]:
            with self.subTest(document=document), patch.object(release, "urlopen", return_value=io.BytesIO(document)):
                with self.assertRaises(ValueError):
                    release.published_versions()

    def test_missing_or_blank_versions_fail(self):
        for document in [metadata(), metadata(None), metadata(" "), metadata("5.5.87-minestom.1", "")]:
            with self.subTest(document=document), patch.object(release, "urlopen", return_value=io.BytesIO(document)):
                with self.assertRaises(ValueError):
                    release.published_versions()


class ReservedVersionTest(unittest.TestCase):
    def test_reads_origin_instead_of_local_tags(self):
        output = "a" * 40 + "\trefs/tags/5.5.87-minestom.12\n"
        with patch.object(release.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, output)) as run:
            self.assertEqual(release.reserved_versions("5.5.87"), ["5.5.87-minestom.12"])
        run.assert_called_once_with(
            ["git", "ls-remote", "--tags", "--refs", "origin", "refs/tags/5.5.87-minestom.*"],
            cwd=release.ROOT, check=True, capture_output=True, text=True, timeout=30,
        )

    def test_no_reserved_tags(self):
        with patch.object(release.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, "")):
            self.assertEqual(release.reserved_versions("5.5.87"), [])

    def test_git_failures_propagate(self):
        for error in [subprocess.CalledProcessError(128, "git"), subprocess.TimeoutExpired("git", 30)]:
            with self.subTest(error=error), patch.object(release.subprocess, "run", side_effect=error):
                with self.assertRaises(type(error)):
                    release.reserved_versions("5.5.87")

    def test_stale_central_metadata_cannot_reuse_reserved_version(self):
        output = "a" * 40 + "\trefs/tags/5.5.87-minestom.12\n"
        with patch.object(release, "luckperms_version", return_value="5.5.87"), \
                patch.object(release, "urlopen", return_value=io.BytesIO(metadata("5.5.87-minestom.2"))), \
                patch.object(release.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, output)), \
                contextlib.redirect_stdout(io.StringIO()) as stdout:
            release.main()
        self.assertEqual(stdout.getvalue(), "5.5.87-minestom.13\n")

    def test_unavailable_metadata_does_not_emit_a_version(self):
        with patch.object(release, "luckperms_version", return_value="5.5.87"), \
                patch.object(release, "urlopen", side_effect=URLError("unavailable")), \
                contextlib.redirect_stdout(io.StringIO()) as stdout:
            with self.assertRaises(URLError):
                release.main()
        self.assertEqual(stdout.getvalue(), "")


if __name__ == "__main__":
    unittest.main()
