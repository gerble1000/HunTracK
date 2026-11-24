#!/usr/bin/env python3
"""Rewrite a PNG as opaque 8-bit RGB (PNG color type 2, no alpha channel)."""

import struct
import sys
import zlib


PNG_SIG = b"\x89PNG\r\n\x1a\n"


def _chunk(tag: bytes, data: bytes) -> bytes:
    crc = zlib.crc32(tag + data) & 0xFFFFFFFF
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", crc)


def _paeth(left: int, up: int, up_left: int) -> int:
    estimate = left + up - up_left
    pa = abs(estimate - left)
    pb = abs(estimate - up)
    pc = abs(estimate - up_left)
    if pa <= pb and pa <= pc:
        return left
    if pb <= pc:
        return up
    return up_left


def _unfilter(raw: bytes, height: int, stride: int, bpp: int) -> list:
    rows = []
    previous = bytearray(stride)
    index = 0
    expected = height * (stride + 1)
    if len(raw) < expected:
        raise ValueError(f"IDAT is short: {len(raw)} bytes, expected at least {expected}")
    for _ in range(height):
        filter_type = raw[index]
        index += 1
        row = bytearray(raw[index : index + stride])
        index += stride
        if filter_type == 0:
            pass
        elif filter_type == 1:
            for x in range(stride):
                left = row[x - bpp] if x >= bpp else 0
                row[x] = (row[x] + left) & 255
        elif filter_type == 2:
            for x in range(stride):
                row[x] = (row[x] + previous[x]) & 255
        elif filter_type == 3:
            for x in range(stride):
                left = row[x - bpp] if x >= bpp else 0
                row[x] = (row[x] + ((left + previous[x]) // 2)) & 255
        elif filter_type == 4:
            for x in range(stride):
                left = row[x - bpp] if x >= bpp else 0
                up_left = previous[x - bpp] if x >= bpp else 0
                row[x] = (row[x] + _paeth(left, previous[x], up_left)) & 255
        else:
            raise ValueError(f"unsupported PNG filter {filter_type}")
        rows.append(row)
        previous = row
    return rows


def decode_png(data: bytes):
    if not data.startswith(PNG_SIG):
        raise ValueError("not a PNG")
    index = 8
    idat = bytearray()
    width = height = bit_depth = color_type = interlace = None
    while index < len(data):
        if index + 8 > len(data):
            raise ValueError("truncated chunk header")
        length = struct.unpack(">I", data[index : index + 4])[0]
        tag = data[index + 4 : index + 8]
        start = index + 8
        end = start + length
        if end + 4 > len(data):
            raise ValueError("truncated chunk")
        chunk = data[start:end]
        index = end + 4
        if tag == b"IHDR":
            width, height, bit_depth, color_type, _, _, interlace = struct.unpack(
                ">IIBBBBB", chunk
            )
        elif tag == b"IDAT":
            idat += chunk
        elif tag == b"IEND":
            break
    if width is None:
        raise ValueError("PNG has no IHDR")
    if interlace != 0:
        raise ValueError("interlaced PNG is not supported")
    if bit_depth != 8 or color_type not in (2, 6):
        raise ValueError(f"unsupported PNG bit depth {bit_depth} color type {color_type}")
    bpp = 4 if color_type == 6 else 3
    stride = width * bpp
    raw = zlib.decompress(bytes(idat))
    rows = _unfilter(raw, height, stride, bpp)
    rgb = bytearray()
    if color_type == 6:
        for row in rows:
            for x in range(0, stride, 4):
                rgb += row[x : x + 3]
    else:
        for row in rows:
            rgb += row
    return width, height, bytes(rgb)


def encode_rgb_png(width: int, height: int, rgb: bytes) -> bytes:
    stride = width * 3
    if len(rgb) != height * stride:
        raise ValueError("RGB buffer has the wrong size")
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        raw += rgb[y * stride : (y + 1) * stride]
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return (
        PNG_SIG
        + _chunk(b"IHDR", ihdr)
        + _chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + _chunk(b"IEND", b"")
    )


def png_info(data: bytes):
    if not data.startswith(PNG_SIG):
        raise ValueError("not a PNG")
    width, height, bit_depth, color_type = struct.unpack(">IIBB", data[16:26])
    return width, height, bit_depth, color_type


def flatten_file(src: str, dest: str) -> tuple:
    with open(src, "rb") as handle:
        width, height, rgb = decode_png(handle.read())
    payload = encode_rgb_png(width, height, rgb)
    info = png_info(payload)
    if info != (width, height, 8, 2):
        raise ValueError(f"encoded PNG is {info}, expected opaque RGB")
    with open(dest, "wb") as handle:
        handle.write(payload)
    return width, height


def _self_test() -> None:
    def sample(color_type: int, filters: list) -> bytes:
        width, height = 3, 2
        bpp = 4 if color_type == 6 else 3
        rows = []
        for y in range(height):
            row = bytearray()
            for x in range(width):
                row += bytes((20 + x * 40, 30 + y * 50, 200, 128)[:bpp])
            rows.append(row)
        raw = bytearray()
        previous = bytearray(width * bpp)
        for y, row in enumerate(rows):
            kind = filters[y]
            stored = bytearray(row)
            if kind == 1:
                for x in range(len(row) - 1, -1, -1):
                    left = row[x - bpp] if x >= bpp else 0
                    stored[x] = (row[x] - left) & 255
            elif kind == 4:
                for x in range(len(row)):
                    left = row[x - bpp] if x >= bpp else 0
                    up_left = previous[x - bpp] if x >= bpp else 0
                    stored[x] = (row[x] - _paeth(left, previous[x], up_left)) & 255
            elif kind != 0:
                raise AssertionError(kind)
            raw.append(kind)
            raw += stored
            previous = bytearray(row)
        ihdr = struct.pack(">IIBBBBB", width, height, 8, color_type, 0, 0, 0)
        return (
            PNG_SIG
            + _chunk(b"IHDR", ihdr)
            + _chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + _chunk(b"IEND", b"")
        )

    import os
    import tempfile

    for color_type, filters in ((6, [0, 0]), (2, [0, 1]), (6, [4, 0])):
        blob = sample(color_type, filters)
        with tempfile.TemporaryDirectory() as tmp:
            src = os.path.join(tmp, "in.png")
            dst = os.path.join(tmp, "out.png")
            with open(src, "wb") as handle:
                handle.write(blob)
            width, height = flatten_file(src, dst)
            assert (width, height) == (3, 2)
            with open(dst, "rb") as handle:
                encoded = handle.read()
            info = png_info(encoded)
            assert info == (3, 2, 8, 2), info
            out_w, out_h, rgb = decode_png(encoded)
            assert (out_w, out_h) == (3, 2)
            assert rgb[0:3] == bytes((20, 30, 200))
            assert rgb[3 * 3 : 3 * 3 + 3] == bytes((20, 80, 200))
    print("flatten_png self-test ok")


def main() -> None:
    if len(sys.argv) == 2 and sys.argv[1] == "--self-test":
        _self_test()
        return
    if len(sys.argv) == 3 and sys.argv[1] == "--size":
        with open(sys.argv[2], "rb") as handle:
            width, height, _ = decode_png(handle.read())
        print(f"{width} {height}")
        return
    if len(sys.argv) != 3:
        print("usage: flatten_png.py INPUT.png OUTPUT.png", file=sys.stderr)
        sys.exit(64)
    width, height = flatten_file(sys.argv[1], sys.argv[2])
    print(f"{width}x{height} color=2")


if __name__ == "__main__":
    main()
