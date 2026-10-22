import assert from "node:assert/strict";
import { Buffer } from "node:buffer";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import ts from "typescript";

const source = await readFile(new URL("../src/accent-colors.ts", import.meta.url), "utf8");
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
});
const { makeAccentColors } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString("base64")}`);

function luminance(color) {
  const channels = color.match(/[a-f\d]{2}/gi).map(channel => {
    const value = parseInt(channel, 16) / 255;
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  });
  return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
}

function assertReadable(foreground, background) {
  const [lighter, darker] = [luminance(foreground), luminance(background)].sort((a, b) => b - a);
  assert.ok((lighter + 0.05) / (darker + 0.05) >= 4.5, `${foreground} on ${background} must have readable contrast`);
}

test("default and malformed accents leave the original palette in place", () => {
  for (const color of ["", "red", "#FFF", "#GGGGGG", "#12345678", "123456"]) {
    assert.equal(makeAccentColors(color, "#141218", true), undefined);
  }
});

test("custom accents keep readable text in light and dark themes", () => {
  const seeds = ["#000000", "#FFFFFF", "#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#00FFFF", "#FF00FF", "#808080", "#6750A4"];
  for (const dark of [false, true]) {
    const background = dark ? "#141218" : "#FFFBFE";
    for (const seed of seeds) {
      const palette = makeAccentColors(seed, background, dark);
      assert.ok(palette);
      assertReadable(palette.primary, background);
      assertReadable(palette.onPrimary, palette.primary);
      assertReadable(palette.onPrimaryContainer, palette.primaryContainer);
      assertReadable(palette.onSecondaryContainer, palette.secondaryContainer);
      assert.equal(palette.tint, palette.primary);
    }
  }
});

test("readable accent colors keep their exact value and normalize hex case", () => {
  assert.equal(makeAccentColors("#6750a4", "#FFFBFE", false).primary, "#6750A4");
  assert.equal(makeAccentColors("#00ff00", "#141218", true).primary, "#00FF00");
});

test("accent containers match the selected hue rather than retaining default purple", () => {
  for (const dark of [false, true]) {
    const background = dark ? "#141218" : "#FFFBFE";
    const red = makeAccentColors("#FF0000", background, dark);
    const blue = makeAccentColors("#0000FF", background, dark);
    assert.notEqual(red.primaryContainer, blue.primaryContainer);
    assert.equal(red.primaryContainer, red.secondaryContainer);
  }
});
