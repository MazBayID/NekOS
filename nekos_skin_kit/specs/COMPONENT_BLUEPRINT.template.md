# Component Blueprint — [component_id]

> Status: draft / measured / runtime-verified  
> Renderer source: `[Kotlin/Compose source file]`

## Purpose and responsibility

Describe what this component draws and explicitly list behavior it does not own (actions, navigation, persistence, gestures, state transitions).

## Asset dependencies

| Asset ID | File | Role | Required / optional | Fallback |
|---|---|---|---|---|
| | | | | |

## Coordinate systems

- Source canvas: [width × height px]
- Display box: [width × height dp or constraint]
- Coordinate origin: top-left unless documented otherwise
- Scale/density mapping: [measured or runtime rule]
- Content bounds: [x, y, width, height px]
- Safe area: [x, y, width, height px]
- Anchor/pivot: [normalized x/y and meaning]

## Geometry and formulas

Define every variable, unit, and valid input constraint.

```text
W_component = ...
H_component = ...
offset_x_dp = ...
offset_y_dp = ...
```

## Placement and layering

- Parent:
- Alignment:
- Margin/padding (dp):
- Offset (dp):
- Z/layer order:
- Overlap and clipping:
- Responsive behavior:

## Rendering rules

- Fit / fill / crop / stretch / tile / nine-slice / three-slice / atlas:
- Fixed regions:
- Stretch regions:
- Crop rectangles:
- Tint / opacity / blend:
- Text baseline / alignment:

## State variants

Document default, selected, pressed, focused, disabled, loading, and empty states where applicable.

## Runtime contract

- Runtime path and loader:
- JSON schema/version:
- Fallback behavior:
- Replaceable by skin without Kotlin changes today? Yes / No / Partial
- Required renderer work:

## Validation checklist

- [ ] Referenced files exist.
- [ ] Pixel dimensions and alpha bounds measured.
- [ ] Formula and coordinate units explicit.
- [ ] Small, normal, and large layouts checked.
- [ ] Text/content remains readable.
- [ ] Missing asset fallback checked.
- [ ] Interaction and accessibility semantics unchanged.
- [ ] Screenshot compared against baseline.
