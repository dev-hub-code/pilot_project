#!/usr/bin/env node
/*
 * Downloads the website's photos from Wikimedia Commons and saves WebP copies at each width the site uses, in
 * public/photos/{name}-{width}.webp. The site serves these files itself, so no photo is loaded from Wikimedia.
 *
 * Run from frontend/ after adding or changing a photo:  node scripts/build-photos.mjs [name ...]
 * Keep SOURCES in step with PHOTOS in src/components/brand/port-photo.tsx.
 */
import { mkdir } from "node:fs/promises";
import path from "node:path";
import sharp from "sharp";

const COMMONS = "https://upload.wikimedia.org/wikipedia/commons";
const OUT = path.join(import.meta.dirname, "..", "public", "photos");
/** Must match WIDTHS in port-photo.tsx. */
const WIDTHS = [500, 960, 1280, 1920];
const QUALITY = 74;

/** Each photo's original on Commons: hash directories and URL-encoded file name. */
const SOURCES = {
  "savannah-cranes": "b/b4/Port_of_Savannah.jpeg",
  "terminal-above": "a/aa/Container_terminal_from_above_%28Unsplash%29.jpg",
  "container-wall": "8/83/Shipping_container_pattern_%28Unsplash%29.jpg",
  "yard-stacks": "c/c9/Shipping_containers_lie_stacked_in_Mogadishu_port_in_Somalia_on_September_17%2C_2015._Mogadishu%27s_port%2C_having_recently_modernized_to_facilitate_the_shipping_of_containers%2C_has_made_it_much_easier_for_%2827447020401%29.jpg",
  "jnpt-cfs": "5/5e/Allcargo_CFS%2C_JNPT%2C_Mumbai.jpg",
  "chennai-cfs": "5/51/Allcargo_CFS%2C_Chennai.jpg",
  "kolkata-port": "7/7a/A_container_ship_barthed_at_Berth_No._7_of_Netaji_Subhas_Dock.png",
  "tuticorin-terminal": "f/f7/Dakshin_Bharat_Gateway_Terminal_of_V.O._Chidambaranar_Port_Authority.jpg",
  "kochi-terminal": "f/fb/Mogral_at_the_International_Container_Transshipment_Terminal%2C_Kochi.jpg",
};

/** Wikimedia throttles and sometimes drops large downloads: try a few times, waiting longer each time. */
async function download(name, url) {
  for (let attempt = 1; ; attempt++) {
    try {
      const response = await fetch(url, { headers: { "User-Agent": "SeaLease-photo-build/1.0" }, signal: AbortSignal.timeout(60_000) });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return Buffer.from(await response.arrayBuffer());
    } catch (error) {
      if (attempt === 4) throw new Error(`${name}: download failed (${error.message})`);
      console.warn(`${name}: ${error.message}, retrying`);
      await new Promise((resolve) => setTimeout(resolve, attempt * 5000));
    }
  }
}

const only = process.argv.slice(2);
await mkdir(OUT, { recursive: true });

for (const [name, file] of Object.entries(SOURCES)) {
  if (only.length > 0 && !only.includes(name)) continue;
  // Commons' own 1920px copy is all we need and far smaller than most originals. It has none for an original
  // that is not wider than that, so fall back to the original then.
  const fileName = file.slice(file.lastIndexOf("/") + 1);
  const original = await download(name, `${COMMONS}/thumb/${file}/1920px-${fileName}`)
    .catch(() => download(name, `${COMMONS}/${file}`));
  const { width } = await sharp(original).metadata();
  for (const w of WIDTHS) {
    const target = path.join(OUT, `${name}-${w}.webp`);
    // Never enlarge: a smaller original is saved at its own width under the larger names.
    const info = await sharp(original).rotate().resize({ width: Math.min(w, width) }).webp({ quality: QUALITY }).toFile(target);
    console.log(`${name}-${w}.webp  ${info.width}x${info.height}  ${(info.size / 1024).toFixed(0)} KB`);
  }
}
