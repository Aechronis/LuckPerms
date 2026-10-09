#!/usr/bin/env python3
"""Check the actual Maven publication before uploading it to Central."""

import argparse
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZipFile


GROUP = "net.aechronis"
ARTIFACT = "luckperms-minestom"
DEPENDENCY = ("net.kyori", "adventure-text-minimessage", "5.2.0")
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def pom_text(element, path):
    return element.findtext("/".join("m:" + part for part in path.split("/")), namespaces=NS)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repository", type=Path)
    parser.add_argument("version")
    parser.add_argument("--signed", action="store_true")
    args = parser.parse_args()
    require(re.fullmatch(r"\d+\.\d+\.\d+-minestom\.[1-9]\d*", args.version), "Invalid Minestom release version")

    folder = args.repository / GROUP.replace(".", "/") / ARTIFACT / args.version
    stem = f"{ARTIFACT}-{args.version}"
    suffixes = (".jar", "-sources.jar", "-javadoc.jar", ".pom", ".module")
    artifacts = [folder / f"{stem}{suffix}" for suffix in suffixes]
    for artifact in artifacts:
        require(artifact.is_file() and artifact.stat().st_size, f"Missing or empty artifact: {artifact}")
        data = artifact.read_bytes()
        for algorithm in ("md5", "sha1", "sha256", "sha512"):
            checksum = artifact.with_name(f"{artifact.name}.{algorithm}")
            if algorithm in ("md5", "sha1") or checksum.exists():
                require(checksum.is_file(), f"Missing checksum: {checksum}")
                require(hashlib.new(algorithm, data).hexdigest() == checksum.read_text().strip(), f"Invalid checksum: {checksum}")
        if args.signed:
            subprocess.run(["gpg", "--batch", "--verify", str(artifact) + ".asc", str(artifact)], check=True)

    pom = ET.parse(folder / f"{stem}.pom").getroot()
    for name in (
        "name", "description", "url", "licenses/license/name", "licenses/license/url",
        "developers/developer/name", "scm/connection", "scm/url",
    ):
        require((pom_text(pom, name) or "").strip(), f"Missing POM {name}")
    for name, expected in (("groupId", GROUP), ("artifactId", ARTIFACT), ("version", args.version)):
        require(pom_text(pom, name) == expected, f"Incorrect POM {name}")
    dependencies = pom.findall("m:dependencies/m:dependency", NS)
    require(len(dependencies) == 1, "The POM must expose only the external MiniMessage dependency")
    for dependency in dependencies:
        coordinate = tuple(pom_text(dependency, name) for name in ("groupId", "artifactId", "version"))
        require(coordinate == DEPENDENCY, f"Unexpected POM dependency: {coordinate}")
        require(pom_text(dependency, "scope") == "runtime", "MiniMessage must be a runtime dependency")
        require(pom_text(dependency, "optional") != "true", "MiniMessage must not be optional")

    metadata = json.loads((folder / f"{stem}.module").read_text())
    for name, expected in (("group", GROUP), ("module", ARTIFACT), ("version", args.version)):
        require(metadata["component"][name] == expected, f"Incorrect module metadata {name}")
    runtime_found = False
    for variant in metadata["variants"]:
        require("available-at" not in variant, f"Unexpected external variant: {variant['name']}")
        require(not variant.get("dependencyConstraints"), f"Unexpected dependency constraints: {variant['name']}")
        dependencies = variant.get("dependencies", [])
        for dependency in dependencies:
            coordinate = (dependency["group"], dependency["module"], dependency.get("version", {}).get("requires"))
            require(coordinate == DEPENDENCY, f"Unexpected module dependency: {coordinate}")
            require(dependency["version"] == {"requires": DEPENDENCY[2]}, "MiniMessage must have a fixed release version")
        if variant.get("attributes", {}).get("org.gradle.usage") == "java-runtime":
            runtime_found = True
            require(len(dependencies) == 1, "Runtime metadata must expose the MiniMessage dependency")
            require(any(item["url"] == f"{stem}.jar" for item in variant.get("files", [])), "Runtime metadata must reference the shaded jar")
        for artifact in variant.get("files", []):
            require(artifact["url"] in {path.name for path in artifacts}, f"Unexpected module artifact: {artifact['url']}")
            data = (folder / artifact["url"]).read_bytes()
            require(len(data) == artifact["size"], f"Incorrect module artifact size: {artifact['url']}")
            require("sha256" in artifact, f"Missing module artifact checksum: {artifact['url']}")
            for algorithm in ("md5", "sha1", "sha256", "sha512"):
                if algorithm in artifact:
                    require(hashlib.new(algorithm, data).hexdigest() == artifact[algorithm], f"Invalid module {algorithm}: {artifact['url']}")
    require(runtime_found, "Missing runtime module variant")

    classes = (
        "me/lucko/luckperms/minestom/LuckPermsMinestom",
        "me/lucko/luckperms/minestom/LPMinestomBootstrap",
        "me/lucko/luckperms/common/plugin/AbstractLuckPermsPlugin",
        "me/lucko/luckperms/common/loader/LoaderBootstrap",
        "net/luckperms/api/LuckPerms",
    )
    with ZipFile(folder / f"{stem}.jar") as archive:
        entries = set(archive.namelist())
        for name in (*classes, "org/h2/Driver"):
            require(name + ".class" in entries, f"Missing shaded class: {name}")
        for prefix in ("net/minestom/", "net/kyori/adventure/", "org/slf4j/"):
            require(not any(name.startswith(prefix) and name.endswith(".class") for name in entries), f"Bundled host classes: {prefix}")
        require("LICENSE.txt" in entries, "Missing LuckPerms MIT license")
        license_text = archive.read("LICENSE.txt").decode("utf-8")
        require("Permission is hereby granted, free of charge" in license_text, "Incorrect LuckPerms MIT license")
        for name in ("luckperms.conf", "luckperms.commodore"):
            require(name in entries, f"Missing runtime resource: {name}")
        bootstrap = archive.read("me/lucko/luckperms/minestom/LPMinestomBootstrap.class")
        require(b"@VERSION@" not in bootstrap and args.version.encode("utf-8") in bootstrap, "Bootstrap version was not substituted with the release version")
    with ZipFile(folder / f"{stem}-sources.jar") as archive:
        for name in classes:
            require(name + ".java" in archive.namelist(), f"Missing published source: {name}")
    with ZipFile(folder / f"{stem}-javadoc.jar") as archive:
        require("me/lucko/luckperms/minestom/LuckPermsMinestom.html" in archive.namelist(), "Missing Minestom API Javadoc")
    print(f"PUBLICATION_OK {GROUP}:{ARTIFACT}:{args.version}")


if __name__ == "__main__":
    main()
