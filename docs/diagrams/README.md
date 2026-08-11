# Rendered diagrams

32 SVG files for the written report: 29 rendered from the Mermaid blocks in
`DigiShield_Technical_Design.md`, and 3 from the BPMN files in `docs/`.

Numbered files keep the order and the section heading they came from, so
`04-5-data-model-er-diagram.svg` is the diagram under *5. Data Model* .

## Regenerating

```bash
# Mermaid -> SVG (one file per ```mermaid block, named after its heading)
npx @mermaid-js/mermaid-cli -i diagram.mmd -o out.svg -b white -c mermaid-config.json

# BPMN -> SVG
npx bpmn-to-image "docs/DigiShield_bpmn_sim_campaign.bpmn:docs/diagrams/bpmn-sim-campaign.svg"
```

`mermaid-config.json` sets `htmlLabels: false`, and that is not cosmetic.

## Why htmlLabels must stay off

By default Mermaid puts label text inside `<foreignObject>`, which is HTML
embedded in SVG. A browser renders it; nothing else does. Rendering the first
attempt through `rsvg-convert` -- the path a LaTeX build takes -- produced boxes
and arrows with **every label missing**, silently, with a zero exit code. 20 of
the 29 diagrams were affected.

With `htmlLabels: false` Mermaid emits real `<text>` elements. All 32 files were
then re-checked by rendering each one through `rsvg-convert` and confirming it
produced an image with text in it.

If you regenerate without that config, the SVGs will still look right in a
browser and lose their text in print.

## One source fix was needed

`19.4 Propagating tenant context` failed to parse: its message read
`SET app.tenant_id = <tenant_id>; query`, and the `;` ends a statement in
Mermaid, leaving `query` dangling. That diagram had never rendered anywhere,
GitHub included. The source now reads `SET app.tenant_id, then query`.
