type RGB = [number, number, number];

function parseHex(value: string): RGB | undefined {
  if (!/^#[0-9a-f]{6}$/i.test(value)) return undefined;
  return [1, 3, 5].map(offset => parseInt(value.slice(offset, offset + 2), 16)) as RGB;
}

function hex(rgb: RGB): string {
  return `#${rgb.map(channel => Math.round(channel).toString(16).padStart(2, "0")).join("")}`.toUpperCase();
}

function blend(from: RGB, to: RGB, amount: number): RGB {
  return from.map((channel, index) => channel + (to[index] - channel) * amount) as RGB;
}

function luminance(rgb: RGB): number {
  const [r, g, b] = rgb.map(channel => {
    const value = Math.round(channel) / 255;
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: RGB, b: RGB): number {
  const first = luminance(a);
  const second = luminance(b);
  return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
}

function foreground(rgb: RGB): string {
  return luminance(rgb) > 0.179 ? "#000000" : "#FFFFFF";
}

/** Keep the chosen hue, adjusting its tone only when needed for readable text. */
export function makeAccentColors(rawColor: string, background: string, dark: boolean) {
  const accent = parseHex(rawColor);
  const surface = parseHex(background);
  if (!accent || !surface) return undefined;

  const target: RGB = dark ? [255, 255, 255] : [0, 0, 0];
  let primary = accent;
  for (let step = 1; contrast(primary, surface) < 4.5 && step <= 100; step++) {
    primary = blend(accent, target, step / 100);
  }
  const container = blend(surface, accent, dark ? 0.24 : 0.16);
  return {
    primary: hex(primary),
    onPrimary: foreground(primary),
    primaryContainer: hex(container),
    onPrimaryContainer: foreground(container),
    secondaryContainer: hex(container),
    onSecondaryContainer: foreground(container),
    tint: hex(primary),
  };
}
