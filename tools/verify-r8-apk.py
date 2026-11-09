"""Check R8 APK structure; this does not validate signatures or device behavior."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import zipfile


XPOSED_MODULE = b"Lio/github/libxposed/api/XposedModule;"
METADATA = "META-INF/xposed/"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(4 * 1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def dex_classes(data):
    """Read definitions, access flags and superclass references, not bytecode."""
    def u32(offset):
        require(0 <= offset <= len(data) - 4, "Out-of-range DEX uint32")
        return struct.unpack_from("<I", data, offset)[0]

    require(len(data) >= 112 and re.fullmatch(rb"dex\n\d{3}\0", data[:8]),
            "Unsupported/truncated DEX")
    require(u32(32) == len(data), "DEX file size differs from header")
    require(u32(36) == 112 and u32(40) == 0x12345678, "Unsupported DEX header/byte order")
    strings_count, strings_offset = u32(56), u32(60)
    types_count, types_offset = u32(64), u32(68)
    classes_count, classes_offset = u32(96), u32(100)
    for count, offset, width, name in ((strings_count, strings_offset, 4, "string"),
                                       (types_count, types_offset, 4, "type"),
                                       (classes_count, classes_offset, 32, "class")):
        require((not count or offset >= 112) and offset + width * count <= len(data),
                f"DEX {name} table truncated")

    def descriptor(type_index):
        require(type_index < types_count, "DEX type index out of range")
        string_index = u32(types_offset + 4 * type_index)
        require(string_index < strings_count, "DEX descriptor index out of range")
        offset = u32(strings_offset + 4 * string_index)
        for _ in range(5):
            require(offset < len(data), "DEX class descriptor truncated")
            byte = data[offset]
            offset += 1
            if not byte & 0x80:
                break
        else:
            raise ValueError("Invalid DEX string ULEB128")
        end = data.find(b"\0", offset)
        require(end >= offset, "Unterminated DEX class descriptor")
        return data[offset:end]

    result = {}
    for index in range(classes_count):
        offset = classes_offset + 32 * index
        name = descriptor(u32(offset))
        require(name.startswith(b"L") and name.endswith(b";"), "Invalid DEX class descriptor")
        require(name not in result, "Duplicate DEX class definition")
        parent_index = u32(offset + 8)
        result[name] = (u32(offset + 4),
                        None if parent_index == 0xffffffff else descriptor(parent_index))
    return result


def metadata_entries(data):
    entries = [line.strip() for line in data.decode("utf-8-sig").splitlines()
               if line.strip() and not line.lstrip().startswith("#")]
    require(entries, "Empty Xposed metadata")
    require(len(entries) == len(set(entries)), "Duplicate metadata entry")
    return entries


def class_descriptor(name):
    return b"L" + name.replace(".", "/").encode("utf-8") + b";"


def verify(project_root, apk, mapping):
    gradle = (project_root / "app/build.gradle").read_text(encoding="utf-8-sig")
    namespace_match = re.search(r'\bnamespace\s+["\']([^"\']+)["\']', gradle)
    require(namespace_match, "Source Android namespace is missing")
    namespace = namespace_match.group(1)
    mapping_text = mapping.read_text(encoding="utf-8-sig")
    require(re.search(r"(?m)^# compiler:\s*R8\s*$", mapping_text), "Input is not an R8 mapping")
    version = re.search(r"(?m)^# compiler_version:\s*(\S+)\s*$", mapping_text)
    require(version, "R8 compiler version is missing from mapping")
    class_mapping = dict(re.findall(r"(?m)^([^\s:#]+) -> ([^\s:]+):\s*$", mapping_text))
    require(class_mapping, "R8 mapping has no class entries")

    definitions = {}
    dex_files = []
    metadata = {}
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), "Duplicate APK ZIP member")
        for filename in ("java_init.list", "scope.list"):
            member = METADATA + filename
            source = (project_root / "app/src/main/resources" / member).read_bytes()
            packaged = archive.read(member)
            source_entries = metadata_entries(source)
            packaged_entries = metadata_entries(packaged)
            require(source_entries == packaged_entries if filename == "java_init.list"
                    else set(source_entries) == set(packaged_entries),
                    "APK/source Xposed metadata differs: " + filename)
            metadata[filename] = {"entries": source_entries,
                                  "sourceSha256": hashlib.sha256(source).hexdigest(),
                                  "apkSha256": hashlib.sha256(packaged).hexdigest()}
        for name in names:
            if re.fullmatch(r"classes(?:\d+)?\.dex", name):
                data = archive.read(name)
                classes = dex_classes(data)
                require(not definitions.keys() & classes.keys(), "Duplicate DEX class definition")
                definitions.update(classes)
                dex_files.append({"name": name, "sha256": hashlib.sha256(data).hexdigest(),
                                  "classDefinitions": len(classes)})
    require(dex_files, "APK contains no DEX")
    require(not any(name.startswith(b"Lio/github/libxposed/api/") for name in definitions)
            and not any(original.startswith("io.github.libxposed.api.")
                        and class_descriptor(target) in definitions
                        for original, target in class_mapping.items()),
            "APK defines compileOnly libxposed API classes")
    entries = metadata["java_init.list"]["entries"]
    for entry in entries:
        name = class_descriptor(entry)
        require(name in definitions, "Entry class is not defined in DEX: " + entry)
        require(definitions[name][0] & 1, "Entry class is not public: " + entry)
        parent = definitions[name][1]
        seen = {name}
        while parent != XPOSED_MODULE and parent in definitions and parent not in seen:
            seen.add(parent)
            parent = definitions[parent][1]
        require(parent == XPOSED_MODULE, "Entry class does not extend XposedModule: " + entry)
        require(class_mapping.get(entry) == entry, "Entry name changed in mapping or absent: " + entry)
    renamed = []
    for original, target in class_mapping.items():
        if not original.startswith(namespace + ".") or original in entries or original == target:
            continue
        relative = original.split("$", 1)[0].replace(".", "/")
        source_exists = any((project_root / "app/src/main" / folder / (relative + suffix)).is_file()
                            for folder in ("java", "kotlin") for suffix in (".java", ".kt"))
        if (source_exists and class_descriptor(target) in definitions
                and class_descriptor(original) not in definitions):
            renamed.append({"original": original, "renamed": target})
    # Generated/inner classes alone do not prove that a source business class was renamed.
    require(any("$" not in item["original"] for item in renamed),
            "No module business class was actually renamed in packaged DEX")
    return {"status": "passed", "validationScope": "r8_apk_structure_only",
            "r8CompilerVersion": version.group(1), "renamedModuleClassCount": len(renamed),
            "renamedModuleClassExamples": renamed[:10], "xposedEntryPoints": entries,
            "xposedScopes": metadata["scope.list"]["entries"], "metadata": metadata,
            "dexFiles": dex_files, "dexClassDefinitionCount": len(definitions)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-root", type=Path, required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--mapping", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = {"status": "failed", "apk": str(args.apk.resolve()),
              "mapping": str(args.mapping.resolve()), "deviceRuntime": "not_validated"}
    try:
        result["apkSha256"] = sha256_file(args.apk)
        result["apkSize"] = args.apk.stat().st_size
        result["mappingSha256"] = sha256_file(args.mapping)
        result.update(verify(args.project_root, args.apk, args.mapping))
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        result.update(status="failed", error=str(error))
    if args.output:
        try:
            require(args.output.resolve() not in (args.apk.resolve(), args.mapping.resolve()),
                    "JSON output must not overwrite APK or mapping")
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        except (ValueError, OSError) as error:
            result.update(status="failed", error=str(error))
    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 0 if result["status"] == "passed" else 1


if __name__ == "__main__":
    sys.exit(main())
