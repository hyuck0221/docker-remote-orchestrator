#!/usr/bin/env python3
"""Generate DRO app icon (1024x1024 PNG) using Pillow."""

import sys
import os
import math

try:
    from PIL import Image, ImageDraw
except ImportError:
    print("Installing Pillow...")
    import subprocess
    subprocess.check_call(
        [sys.executable, "-m", "pip", "install", "Pillow", "-q", "--break-system-packages"],
    )
    from PIL import Image, ImageDraw


def lerp(a, b, t):
    return int(a + (b - a) * t)


def generate_icon(output_dir):
    os.makedirs(output_dir, exist_ok=True)
    size = 1024
    cx, cy = size // 2, size // 2

    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    # Background: rounded rectangle with diagonal gradient (deep navy → teal)
    bg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bg_draw = ImageDraw.Draw(bg)
    radius = int(size * 0.22)

    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * size)
            r = lerp(15, 0, t)
            g = lerp(32, 105, t)
            b = lerp(80, 148, t)
            bg.putpixel((x, y), (r, g, b, 255))

    mask = Image.new("L", (size, size), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.rounded_rectangle([(0, 0), (size - 1, size - 1)], radius=radius, fill=255)
    bg.putalpha(mask)
    img = Image.alpha_composite(img, bg)
    draw = ImageDraw.Draw(img)

    # --- Design: Central hub with orbiting container nodes ---

    # Outer ring (subtle)
    ring_r = int(size * 0.32)
    ring_w = 3
    for angle_deg in range(360):
        angle = math.radians(angle_deg)
        alpha = int(40 + 25 * math.sin(angle * 3))
        for w in range(ring_w):
            rx = cx + int((ring_r + w) * math.cos(angle))
            ry = cy + int((ring_r + w) * math.sin(angle))
            if 0 <= rx < size and 0 <= ry < size:
                img.putpixel((rx, ry), (150, 220, 255, alpha))

    # Satellite nodes (6 nodes around the center)
    node_count = 6
    orbit_r = int(size * 0.30)
    node_size = int(size * 0.105)
    node_r = int(node_size * 0.25)

    node_positions = []
    for i in range(node_count):
        angle = math.radians(i * 60 - 90)
        nx = cx + int(orbit_r * math.cos(angle))
        ny = cy + int(orbit_r * math.sin(angle))
        node_positions.append((nx, ny))

    # Connection lines from center to each node
    for nx, ny in node_positions:
        # Draw dashed/gradient line
        steps = 40
        for s in range(steps):
            t = s / steps
            lx = int(cx + (nx - cx) * t)
            ly = int(cy + (ny - cy) * t)
            # Fade in from center, fade out near node
            alpha = int(90 * math.sin(t * math.pi))
            line_w = 3
            draw.ellipse(
                [(lx - line_w, ly - line_w), (lx + line_w, ly + line_w)],
                fill=(130, 210, 255, alpha),
            )

    # Cross-connections between adjacent nodes (mesh network feel)
    for i in range(node_count):
        j = (i + 1) % node_count
        ax, ay = node_positions[i]
        bx, by = node_positions[j]
        steps = 25
        for s in range(steps):
            t = s / steps
            lx = int(ax + (bx - ax) * t)
            ly = int(ay + (by - ay) * t)
            alpha = int(35 * math.sin(t * math.pi))
            draw.ellipse(
                [(lx - 1, ly - 1), (lx + 1, ly + 1)],
                fill=(100, 190, 240, alpha),
            )

    # Draw satellite nodes as container-shaped boxes (rounded rects)
    for i, (nx, ny) in enumerate(node_positions):
        half = node_size // 2
        box = [(nx - half, ny - half), (nx + half, ny + half)]

        # Node glow
        glow_layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        glow_draw = ImageDraw.Draw(glow_layer)
        for g_off in range(15, 0, -1):
            alpha = int(8 * (15 - g_off))
            glow_draw.rounded_rectangle(
                [(box[0][0] - g_off, box[0][1] - g_off),
                 (box[1][0] + g_off, box[1][1] + g_off)],
                radius=node_r + g_off,
                fill=(80, 180, 255, alpha),
            )
        img = Image.alpha_composite(img, glow_layer)
        draw = ImageDraw.Draw(img)

        # Node body - alternating slight color variations
        colors = [
            (45, 140, 210, 230),
            (35, 160, 195, 230),
            (55, 130, 220, 230),
            (40, 170, 200, 230),
            (50, 145, 215, 230),
            (30, 155, 205, 230),
        ]
        draw.rounded_rectangle(box, radius=node_r, fill=colors[i % len(colors)])

        # Inner container detail: two horizontal lines
        line_y1 = ny - int(half * 0.25)
        line_y2 = ny + int(half * 0.25)
        line_margin = int(half * 0.35)
        draw.line(
            [(nx - line_margin, line_y1), (nx + line_margin, line_y1)],
            fill=(255, 255, 255, 140), width=3,
        )
        draw.line(
            [(nx - line_margin, line_y2), (nx + line_margin, line_y2)],
            fill=(255, 255, 255, 100), width=3,
        )

        # Small dot indicator (top-right of node)
        dot_x = nx + int(half * 0.55)
        dot_y = ny - int(half * 0.55)
        dot_r = int(size * 0.012)
        draw.ellipse(
            [(dot_x - dot_r, dot_y - dot_r), (dot_x + dot_r, dot_y + dot_r)],
            fill=(100, 255, 170, 220),
        )

    # Central hub: larger hexagonal/circular node
    hub_r = int(size * 0.10)

    # Hub glow
    glow_layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow_layer)
    for g_off in range(25, 0, -1):
        alpha = int(7 * (25 - g_off))
        glow_draw.ellipse(
            [(cx - hub_r - g_off, cy - hub_r - g_off),
             (cx + hub_r + g_off, cy + hub_r + g_off)],
            fill=(100, 200, 255, alpha),
        )
    img = Image.alpha_composite(img, glow_layer)
    draw = ImageDraw.Draw(img)

    # Hub body
    draw.ellipse(
        [(cx - hub_r, cy - hub_r), (cx + hub_r, cy + hub_r)],
        fill=(20, 120, 200, 245),
    )

    # Hub inner ring
    inner_r = int(hub_r * 0.65)
    draw.ellipse(
        [(cx - inner_r, cy - inner_r), (cx + inner_r, cy + inner_r)],
        outline=(255, 255, 255, 160), width=3,
    )

    # Hub center: play/orchestrate triangle symbol
    tri_r = int(hub_r * 0.35)
    tri_offset = int(tri_r * 0.15)  # slight right offset for visual balance
    points = []
    for i in range(3):
        angle = math.radians(i * 120 - 30)
        px = cx + tri_offset + int(tri_r * math.cos(angle))
        py = cy + int(tri_r * math.sin(angle))
        points.append((px, py))
    draw.polygon(points, fill=(255, 255, 255, 220))

    # Save
    output_path = os.path.join(output_dir, "icon.png")
    img.save(output_path, "PNG")
    print(f"Generated: {output_path}")
    return output_path


if __name__ == "__main__":
    output_dir = sys.argv[1] if len(sys.argv) > 1 else "desktop/build/generated/icons"
    generate_icon(output_dir)
