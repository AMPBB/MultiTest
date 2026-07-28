#!/usr/bin/env python3
import argparse
import re
from pathlib import Path


TEXTURE_WIDTH = 512
TEXTURE_HEIGHT = 512
PATTERN = re.compile(
    r"^(?P<no>\d+)_t0x[0-9a-fA-F]+_x(?P<x>\d+)_y(?P<y>\d+)_w(?P<w>\d+)_h(?P<h>\d+)_texsubimage2d$"
)


def parse_args():
    parser = argparse.ArgumentParser(
        description="Combine GL_RED glTexSubImage2D dumps into one 512x512 texture file."
    )
    parser.add_argument(
        "--dir",
        default=".",
        help="Directory containing *_texsubimage2d files. Default: current directory.",
    )
    parser.add_argument(
        "--output",
        default="texture",
        help="Output texture filename. Default: texture.",
    )
    return parser.parse_args()


def find_texsub_files(directory):
    items = []
    for path in directory.iterdir():
        if not path.is_file():
            continue
        match = PATTERN.match(path.name)
        if not match:
            continue
        items.append(
            {
                "path": path,
                "no": int(match.group("no")),
                "x": int(match.group("x")),
                "y": int(match.group("y")),
                "w": int(match.group("w")),
                "h": int(match.group("h")),
            }
        )
    items.sort(key=lambda item: item["no"])
    return items


def guess_source_stride(data_size, width, height, filename):
    if height <= 0:
        raise ValueError(f"{filename}: invalid height {height}")
    if data_size < width * height:
        raise ValueError(
            f"{filename}: data is shorter than w*h, bytes={data_size}, w*h={width * height}"
        )
    if data_size % height == 0 and data_size // height >= width:
        return data_size // height
    return width


def apply_texsub(texture, item):
    data = item["path"].read_bytes()
    x = item["x"]
    y = item["y"]
    width = item["w"]
    height = item["h"]
    stride = guess_source_stride(len(data), width, height, item["path"].name)

    if x >= TEXTURE_WIDTH or y >= TEXTURE_HEIGHT:
        print(f"skip outside texture: {item['path'].name}")
        return

    copy_width = min(width, TEXTURE_WIDTH - x)
    copy_height = min(height, TEXTURE_HEIGHT - y)
    for row in range(copy_height):
        src_offset = row * stride
        dst_offset = (y + row) * TEXTURE_WIDTH + x
        texture[dst_offset : dst_offset + copy_width] = data[
            src_offset : src_offset + copy_width
        ]

    print(
        f"apply no={item['no']} x={x} y={y} w={width} h={height} "
        f"stride={stride} file={item['path'].name}"
    )


def main():
    args = parse_args()
    directory = Path(args.dir).resolve()
    output = directory / args.output

    texsub_files = find_texsub_files(directory)
    if not texsub_files:
        raise SystemExit(f"no texsubimage2d files found in {directory}")

    texture = bytearray(TEXTURE_WIDTH * TEXTURE_HEIGHT)
    for item in texsub_files:
        apply_texsub(texture, item)

    output.write_bytes(texture)
    print(
        f"wrote {output} bytes={len(texture)} size={TEXTURE_WIDTH}x{TEXTURE_HEIGHT} GL_RED"
    )


if __name__ == "__main__":
    main()
