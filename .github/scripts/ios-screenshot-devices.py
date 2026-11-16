#!/usr/bin/env python3
"""Choose simulator devices for the App Store screenshot sizes.

Prints a JSON plan. With --self-test, checks the selection rules and does
not talk to simctl.
"""

import json
import subprocess
import sys

PHONE_69 = ("iPhone 17 Pro Max", "iPhone 16 Pro Max")
PHONE_63 = ("iPhone 17 Pro", "iPhone 16 Pro")
SLOT_SPECS = (
    ("iphone-6.9", 1320, 2868, PHONE_69),
    ("iphone-6.3", 1206, 2622, PHONE_63),
    ("ipad-13", 2064, 2752, None),
)


def _version_key(version: str):
    parts = []
    for piece in version.split("."):
        try:
            parts.append(int(piece))
        except ValueError:
            parts.append(0)
    return tuple(parts)


def _ipad_rank(name: str):
    # Prefer a newer iPad Pro 13-inch (M5 before M4) when several exist.
    marker = name.rfind("(M")
    generation = 0
    if marker != -1:
        digits = []
        for char in name[marker + 2 :]:
            if char.isdigit():
                digits.append(char)
            else:
                break
        if digits:
            generation = int("".join(digits))
    return (generation, name)


def _is_ipad_13(name: str) -> bool:
    return name.startswith("iPad Pro 13-inch")


def select_plans(runtimes, devices_by_runtime, devicetypes):
    """Return plans, newest runtime first, each with one device per slot.

    A plan is only emitted when that runtime can supply every slot. Exact
    phone names are required so an iPhone 17 Pro Max is never used for the
    6.3-inch slot. iPhone 17 is preferred and iPhone 16 Pro is the fallback
    with the same pixel size.
    """
    type_names = {item.get("name") for item in devicetypes}
    ios = []
    for runtime in runtimes:
        identifier = runtime.get("identifier", "")
        name = runtime.get("name", "")
        if runtime.get("isAvailable", True) is False:
            continue
        if "iOS" not in name and "SimRuntime.iOS" not in identifier:
            continue
        ios.append(runtime)
    ios.sort(key=lambda item: _version_key(str(item.get("version", "0"))), reverse=True)

    plans = []
    for runtime in ios:
        identifier = runtime["identifier"]
        available = {}
        for device in devices_by_runtime.get(identifier, []):
            if device.get("isAvailable", True) is False:
                continue
            available.setdefault(device["name"], device["udid"])
        slots = []
        missing = False
        for folder, width, height, names in SLOT_SPECS:
            chosen = None
            if names is None:
                ipads = [device_name for device_name in available if _is_ipad_13(device_name)]
                if not ipads:
                    creatable = sorted(
                        (device_name for device_name in type_names if _is_ipad_13(device_name)),
                        key=_ipad_rank,
                        reverse=True,
                    )
                    if creatable:
                        chosen = {"name": creatable[0], "udid": None, "create": True}
                else:
                    best = sorted(ipads, key=_ipad_rank, reverse=True)[0]
                    chosen = {"name": best, "udid": available[best], "create": False}
            else:
                for device_name in names:
                    if device_name in available:
                        chosen = {"name": device_name, "udid": available[device_name], "create": False}
                        break
                if chosen is None:
                    for device_name in names:
                        if device_name in type_names:
                            chosen = {"name": device_name, "udid": None, "create": True}
                            break
            if chosen is None:
                missing = True
                break
            chosen.update({"folder": folder, "width": width, "height": height})
            slots.append(chosen)
        if missing:
            continue
        plans.append(
            {
                "runtime": identifier,
                "runtime_name": f"{runtime.get('name', 'iOS')} {runtime.get('version', '')}".strip(),
                "slots": slots,
            }
        )
    return plans


def _load_simctl():
    runtimes = json.loads(
        subprocess.check_output(["xcrun", "simctl", "list", "runtimes", "-j"], text=True)
    ).get("runtimes", [])
    devices = json.loads(
        subprocess.check_output(["xcrun", "simctl", "list", "devices", "available", "-j"], text=True)
    ).get("devices", {})
    devicetypes = json.loads(
        subprocess.check_output(["xcrun", "simctl", "list", "devicetypes", "-j"], text=True)
    ).get("devicetypes", [])
    return runtimes, devices, devicetypes


def _create_missing(plans):
    for plan in plans:
        for slot in plan["slots"]:
            if not slot.get("create"):
                continue
            created = subprocess.check_output(
                [
                    "xcrun",
                    "simctl",
                    "create",
                    slot["name"],
                    slot["name"],
                    plan["runtime"],
                ],
                text=True,
            ).strip()
            slot["udid"] = created
            slot["create"] = False
    return plans


def _self_test():
    runtimes = [
        {"identifier": "com.apple.CoreSimulator.SimRuntime.iOS-18-6", "name": "iOS 18.6", "version": "18.6", "isAvailable": True},
        {"identifier": "com.apple.CoreSimulator.SimRuntime.iOS-26-1", "name": "iOS 26.1", "version": "26.1", "isAvailable": True},
        {"identifier": "com.apple.CoreSimulator.SimRuntime.iOS-17-5", "name": "iOS 17.5", "version": "17.5", "isAvailable": False},
    ]
    devices = {
        "com.apple.CoreSimulator.SimRuntime.iOS-26-1": [
            {"name": "iPhone 17 Pro Max", "udid": "MAX17", "isAvailable": True},
            {"name": "iPhone 17 Pro", "udid": "PRO17", "isAvailable": True},
            {"name": "iPhone 17", "udid": "PLAIN17", "isAvailable": True},
            {"name": "iPad Pro 13-inch (M4)", "udid": "PAD-M4", "isAvailable": True},
            {"name": "iPad Pro 13-inch (M5)", "udid": "PAD-M5", "isAvailable": True},
            {"name": "iPad Pro 11-inch (M4)", "udid": "PAD-11", "isAvailable": True},
            {"name": "iPhone 16 Pro Max", "udid": "MAX16", "isAvailable": False},
        ],
        "com.apple.CoreSimulator.SimRuntime.iOS-18-6": [
            {"name": "iPhone 16 Pro Max", "udid": "MAX16B", "isAvailable": True},
            {"name": "iPhone 16 Pro", "udid": "PRO16", "isAvailable": True},
            {"name": "iPhone 16", "udid": "PLAIN16", "isAvailable": True},
            {"name": "iPad Pro 13-inch (M4)", "udid": "PAD18", "isAvailable": True},
        ],
    }
    types = [{"name": "iPhone 17 Pro Max"}, {"name": "iPad Pro 13-inch (M5)"}]
    plans = select_plans(runtimes, devices, types)
    assert plans, "expected a plan"
    assert plans[0]["runtime"].endswith("iOS-26-1")
    by_folder = {slot["folder"]: slot for slot in plans[0]["slots"]}
    assert by_folder["iphone-6.9"]["udid"] == "MAX17"
    assert by_folder["iphone-6.3"]["udid"] == "PRO17"
    assert by_folder["iphone-6.3"]["name"] == "iPhone 17 Pro"
    assert by_folder["ipad-13"]["udid"] == "PAD-M5"
    assert plans[1]["slots"][0]["name"] == "iPhone 16 Pro Max"

    only_types = select_plans(
        [{"identifier": "com.apple.CoreSimulator.SimRuntime.iOS-26-0", "name": "iOS 26.0", "version": "26.0", "isAvailable": True}],
        {"com.apple.CoreSimulator.SimRuntime.iOS-26-0": []},
        [
            {"name": "iPhone 17 Pro Max"},
            {"name": "iPhone 17 Pro"},
            {"name": "iPad Pro 13-inch (M4)"},
        ],
    )
    assert only_types[0]["slots"][0]["create"] is True
    assert only_types[0]["slots"][2]["name"] == "iPad Pro 13-inch (M4)"

    no_ipad = select_plans(
        [{"identifier": "com.apple.CoreSimulator.SimRuntime.iOS-18-0", "name": "iOS 18.0", "version": "18.0", "isAvailable": True}],
        {
            "com.apple.CoreSimulator.SimRuntime.iOS-18-0": [
                {"name": "iPhone 16 Pro Max", "udid": "A", "isAvailable": True},
                {"name": "iPhone 16 Pro", "udid": "B", "isAvailable": True},
            ]
        },
        [],
    )
    assert no_ipad == []
    print("ios-screenshot-devices self-test ok")


def main():
    if "--self-test" in sys.argv:
        _self_test()
        return
    runtimes, devices, devicetypes = _load_simctl()
    plans = select_plans(runtimes, devices, devicetypes)
    if not plans:
        print("No iOS runtime has an iPhone Pro Max, an iPhone Pro, and an iPad Pro 13-inch.", file=sys.stderr)
        print(json.dumps({"runtimes": runtimes, "devices": devices}, indent=2)[:8000], file=sys.stderr)
        sys.exit(1)
    plans = _create_missing(plans)
    json.dump({"plans": plans}, sys.stdout, indent=2)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
