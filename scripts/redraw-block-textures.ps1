param([string[]]$Only = @())
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$textureRoot = Join-Path $PSScriptRoot '..\src\main\resources\assets\ars_arcane_matrix\textures\block'
$textureRoot = [System.IO.Path]::GetFullPath($textureRoot)

# All artwork below is drawn from geometric primitives. No upstream assets
# are read, recolored, traced, or composited into the generated textures.

function Color([string]$hex) {
    return [System.Drawing.ColorTranslator]::FromHtml($hex)
}

$script:bitmap = $null
$script:graphics = $null
$script:target = $null

function Begin-Texture([string]$name, [string]$background = '#00000000') {
    $script:bitmap = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $script:graphics = [System.Drawing.Graphics]::FromImage($script:bitmap)
    $script:graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $script:graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
    $script:graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $script:graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $script:graphics.Clear((Color $background))
    $script:target = Join-Path $textureRoot $name
}

function Rect([int]$x, [int]$y, [int]$w, [int]$h, [string]$color) {
    $brush = [System.Drawing.SolidBrush]::new((Color $color))
    try { $script:graphics.FillRectangle($brush, $x, $y, $w, $h) } finally { $brush.Dispose() }
}

function Pixel([int]$x, [int]$y, [string]$color) {
    if ($x -ge 0 -and $x -lt 32 -and $y -ge 0 -and $y -lt 32) {
        $script:bitmap.SetPixel($x, $y, (Color $color))
    }
}

function Line([int]$x0, [int]$y0, [int]$x1, [int]$y1, [string]$color, [int]$width = 1) {
    $dx = [Math]::Abs($x1 - $x0)
    $sx = if ($x0 -lt $x1) { 1 } else { -1 }
    $dy = -[Math]::Abs($y1 - $y0)
    $sy = if ($y0 -lt $y1) { 1 } else { -1 }
    $err = $dx + $dy
    while ($true) {
        $half = [Math]::Floor($width / 2)
        Rect ($x0 - $half) ($y0 - $half) $width $width $color
        if ($x0 -eq $x1 -and $y0 -eq $y1) { break }
        $twice = 2 * $err
        if ($twice -ge $dy) { $err += $dy; $x0 += $sx }
        if ($twice -le $dx) { $err += $dx; $y0 += $sy }
    }
}

function End-Texture {
    if ($Only.Count -eq 0 -or [IO.Path]::GetFileName($script:target) -in $Only) {
        $script:bitmap.Save($script:target, [System.Drawing.Imaging.ImageFormat]::Png)
        Write-Output ([IO.Path]::GetFileName($script:target))
    }
    $script:graphics.Dispose()
    $script:bitmap.Dispose()
    $script:graphics = $null
    $script:bitmap = $null
}

function Draw-Casing([string]$accent = '#7C43A5', [string]$metal = '#B5633D', [string]$center = '#21162F') {
    Rect 0 0 32 32 '#120D1C'
    Rect 1 1 30 30 '#2A1C3B'
    Rect 3 3 26 26 $metal
    Rect 5 5 22 22 '#432A58'
    Rect 7 7 18 18 $center
    Rect 1 1 6 6 '#71402F'; Rect 25 1 6 6 '#71402F'
    Rect 1 25 6 6 '#71402F'; Rect 25 25 6 6 '#71402F'
    Rect 3 3 3 3 '#E09556'; Rect 26 3 3 3 '#E09556'
    Rect 3 26 3 3 '#E09556'; Rect 26 26 3 3 '#E09556'
    Rect 7 5 18 2 $accent; Rect 7 25 18 2 $accent
    Rect 5 7 2 18 $accent; Rect 25 7 2 18 $accent
}

function Draw-ArsCasing {
    # Historical function name retained for script compatibility.
    Rect 0 0 32 32 '#302837'
    Rect 2 2 28 28 '#69566F'
    Rect 4 4 24 24 '#44394D'
    Rect 6 6 20 20 '#514459'
    Rect 2 2 28 2 '#958199'
    Rect 2 4 2 26 '#7B6983'
    Rect 4 28 26 2 '#302837'
    foreach ($x in @(3, 25)) {
        foreach ($y in @(3, 25)) {
            Rect $x $y 4 4 '#9A684D'
            Rect $x $y 3 1 '#C29570'
        }
    }
}

function Draw-MachinePanel([string]$accent) {
    Rect 5 5 22 22 '#2B203A'
    Rect 7 7 18 18 '#3B2C4B'
    Rect 8 8 7 5 '#4A3658'; Rect 17 8 7 5 '#503A5E'
    Rect 8 15 9 9 '#32263F'; Rect 19 15 5 9 '#463451'
    Rect 5 5 3 3 '#D8B83D'; Rect 24 5 3 3 '#FFF0A0'
    Rect 5 24 3 3 '#FFF0A0'; Rect 24 24 3 3 '#D8B83D'
    Rect 9 6 14 1 $accent; Rect 9 25 14 1 $accent
}

function Draw-Gem([int]$cx, [int]$cy, [string]$outer = '#7C43A5', [string]$inner = '#D672FF') {
    Rect ($cx - 3) ($cy - 5) 6 10 $outer
    Rect ($cx - 5) ($cy - 3) 10 6 $outer
    Rect ($cx - 2) ($cy - 3) 4 6 $inner
    Rect ($cx - 3) ($cy - 2) 6 4 $inner
    Rect ($cx - 1) ($cy - 2) 2 2 '#F4C8FF'
}

function Draw-Ring([int]$cx, [int]$cy, [int]$radius, [string]$color) {
    for ($x = -$radius; $x -le $radius; $x++) {
        for ($y = -$radius; $y -le $radius; $y++) {
            $d = $x * $x + $y * $y
            if ($d -le $radius * $radius -and $d -ge ($radius - 2) * ($radius - 2)) {
                Pixel ($cx + $x) ($cy + $y) $color
            }
        }
    }
}

# Shared matrix frame: source-gem center with sourcebound-copper edges.
Begin-Texture 'arcane_structural_frame_solid_hd.png'
Draw-Casing '#77418F' '#9A573C' '#4B2C61'
Rect 9 9 14 14 '#59306F'; Rect 11 11 10 10 '#71388D'; Rect 13 13 6 6 '#914EAD'; Rect 14 14 4 4 '#B66FCB'
End-Texture

Begin-Texture 'arcane_structural_frame.png'
Draw-Casing '#77418F' '#9A573C' '#4B2C61'
Rect 9 9 14 14 '#59306F'; Rect 11 11 10 10 '#71388D'; Rect 13 13 6 6 '#914EAD'; Rect 14 14 4 4 '#B66FCB'
End-Texture

# Processor: framed inset with a large pickaxe and fractured ore.
Begin-Texture 'arcane_processor_core_side.png'
Draw-MachinePanel '#31CFC4'
Line 11 22 21 10 '#815524' 3
Line 12 11 20 8 '#76F2EA' 3
Line 19 9 23 13 '#37CFC8' 3
Rect 11 18 5 5 '#5F6870'; Rect 10 20 2 3 '#8C98A0'; Line 12 20 15 18 '#B8C1C7'
End-Texture

# Smelter: an arcane furnace mouth; lit variant gets a broad flame.
Begin-Texture 'arcane_smelter_front.png'
Draw-MachinePanel '#A86445'
Rect 11 12 10 9 '#08070B'; Rect 9 10 14 3 '#5A3540'; Rect 10 21 12 2 '#6A4533'
Rect 13 15 6 4 '#632419'; Rect 15 14 2 2 '#D8692C'
End-Texture

Begin-Texture 'arcane_smelter_front_on.png'
Draw-MachinePanel '#E8893F'
Rect 11 12 10 9 '#180A08'; Rect 9 10 14 3 '#7B4432'; Rect 10 21 12 2 '#9D5C32'
Rect 12 16 8 5 '#D8411E'; Rect 14 13 4 7 '#FF7C21'; Rect 15 14 2 4 '#FFE079'
End-Texture

Begin-Texture 'arcane_smelter_side.png'
Draw-MachinePanel '#B26042'
Rect 14 12 4 10 '#7C392B'; Rect 12 14 8 6 '#34171A'; Rect 14 16 4 2 '#D9602F'
End-Texture

# Crusher: a large mortar, pestle, and powder pile. This remains legible in the GUI.
Begin-Texture 'arcane_crusher_item_front.png'
Draw-MachinePanel '#78BFE0'
Line 21 9 15 17 '#D5E3E8' 4
Line 22 8 24 10 '#8FA4AD' 2
Rect 10 17 12 3 '#718891'
Rect 11 20 10 3 '#9EAFB5'
Rect 13 23 6 2 '#D6E2E5'
Pixel 9 14 '#C9D9DE'; Pixel 11 12 '#91B7C7'; Pixel 23 15 '#E4EEF0'
End-Texture

# Independently drawn low-detail material atlas for the animated chamber.
# Deliberately uses broad bands instead of the original texture's pixel layout.
Begin-Texture 'advanced_imbuement_chamber.png' '#44394D'
Rect 0 0 32 6 '#69566F'; Rect 0 0 32 2 '#958199'
Rect 0 6 32 3 '#9A684D'; Rect 0 6 32 1 '#C29570'
Rect 0 14 32 2 '#302837'; Rect 0 16 32 6 '#69566F'
Rect 0 22 32 3 '#9A684D'; Rect 0 22 32 1 '#C29570'
Rect 0 30 32 2 '#302837'
foreach ($x in @(4, 20)) {
    Rect $x 10 8 2 '#825F9B'
    Rect ($x + 3) 9 2 4 '#B595CC'
    Rect $x 26 8 2 '#825F9B'
}
End-Texture
Begin-Texture 'arcane_amplifier.png'
Draw-Casing '#B65CE2' '#C48E42' '#3C1C50'
Line 16 7 16 25 '#D86FFF' 2; Line 7 16 25 16 '#D86FFF' 2
Draw-Gem 16 16 '#8A3DB3' '#E38CFF'
End-Texture

Begin-Texture 'arcane_imbuement_core.png'
Draw-Casing '#E0B846' '#C68B3B' '#392347'
Draw-Gem 16 16 '#A24AC4' '#E292FF'
Line 8 16 12 16 '#E9C95B' 2; Line 20 16 24 16 '#E9C95B' 2
End-Texture

Begin-Texture 'matrix_core.png'
Rect 0 0 32 32 '#160D2A'; Rect 2 2 28 28 '#21133E'; Rect 5 5 22 22 '#321A5C'
Draw-Ring 16 16 10 '#4C2790'; Draw-Ring 16 16 7 '#6A35B9'; Draw-Gem 16 16 '#6D38B1' '#A76BE5'
Line 4 4 10 10 '#6A3AB0' 2; Line 28 4 22 10 '#6A3AB0' 2; Line 4 28 10 22 '#6A3AB0' 2; Line 28 28 22 22 '#6A3AB0' 2
End-Texture

# Source-stone furnace fronts retain the Ars palette, with fewer pixels competing.
Begin-Texture 'source_stone_furnace_front.png'
Rect 0 0 32 32 '#34293E'; Rect 2 2 28 28 '#50415D'; Rect 5 4 22 4 '#74627F'; Rect 5 24 22 4 '#2D2436'
Rect 7 9 18 14 '#17121D'; Rect 9 11 14 10 '#0A090D'; Rect 5 9 2 14 '#7A667F'; Rect 25 9 2 14 '#7A667F'
Rect 2 2 3 3 '#C7A94E'; Rect 27 2 3 3 '#C7A94E'; Rect 2 27 3 3 '#C7A94E'; Rect 27 27 3 3 '#C7A94E'
End-Texture

Begin-Texture 'source_stone_furnace_front_on.png'
Rect 0 0 32 32 '#34293E'; Rect 2 2 28 28 '#50415D'; Rect 5 4 22 4 '#74627F'; Rect 5 24 22 4 '#2D2436'
Rect 7 9 18 14 '#231016'; Rect 9 11 14 10 '#6B1718'; Rect 10 16 12 5 '#E4431C'; Rect 12 13 8 7 '#FF7A1F'; Rect 14 13 4 4 '#FFE169'
Rect 2 2 3 3 '#C7A94E'; Rect 27 2 3 3 '#C7A94E'; Rect 2 27 3 3 '#C7A94E'; Rect 27 27 3 3 '#C7A94E'
End-Texture

# Wixie automation family.
Begin-Texture 'wixie_order_terminal.png'
Draw-ArsCasing
Rect 10 8 13 17 '#E8D4A4'; Rect 12 10 9 13 '#CDAF77'; Rect 9 9 2 13 '#A77A4B'
Line 13 17 16 20 '#7A3EA0' 2; Line 16 20 21 13 '#7A3EA0' 2
End-Texture

Begin-Texture 'wixie_pattern_provider.png'
Draw-ArsCasing
for ($gy = 0; $gy -lt 3; $gy++) { for ($gx = 0; $gx -lt 3; $gx++) { Rect (9 + $gx * 5) (9 + $gy * 5) 4 4 '#423052' } }
Rect 14 14 4 4 '#D063EC'; Pixel 15 15 '#FFE7FF'
End-Texture

Begin-Texture 'automatic_stock_requester.png'
Draw-ArsCasing
Rect 9 9 14 10 '#25202F'; Rect 11 11 10 6 '#53505E'
Line 16 7 16 15 '#9AF4EF' 2; Line 12 12 16 16 '#9AF4EF' 2; Line 20 12 16 16 '#9AF4EF' 2
Rect 11 21 10 3 '#78509B'; Rect 14 19 4 2 '#9A6EC0'
End-Texture

# Storage directory: large cells and one search lens.
Begin-Texture 'storage_grid_directory.png'
Draw-ArsCasing
for ($gy = 0; $gy -lt 3; $gy++) { for ($gx = 0; $gx -lt 3; $gx++) { Rect (8 + $gx * 6) (8 + $gy * 6) 5 5 '#353047'; Rect (9 + $gx * 6) (9 + $gy * 6) 3 2 '#56506A' } }
Draw-Ring 20 20 5 '#64E4E2'; Line 23 23 27 27 '#64E4E2' 2
End-Texture

# Integrated relay: one dominant source orb and four transfer paths.
Begin-Texture 'integrated_source_relay.png'
Draw-ArsCasing
Line 16 5 16 12 '#E2B64B' 2; Line 16 20 16 27 '#E2B64B' 2
Line 5 16 12 16 '#44DAD6' 2; Line 20 16 27 16 '#44DAD6' 2
Draw-Gem 16 16 '#8739B4' '#DC73FA'
Rect 14 3 4 3 '#E2B64B'; Rect 14 26 4 3 '#E2B64B'; Rect 3 14 3 4 '#44DAD6'; Rect 26 14 3 4 '#44DAD6'
End-Texture

# Dimension anchor: broad compass points, no micro-runes.
Begin-Texture 'dimension_anchor.png'
Draw-ArsCasing
Draw-Ring 16 16 9 '#5B6768'; Draw-Ring 16 16 6 '#C9A544'
Line 16 6 16 26 '#E0C25C' 2; Line 6 16 26 16 '#E0C25C' 2
Line 16 7 20 16 '#E7D477' 2; Line 16 25 12 16 '#7E5A2E' 2
Draw-Gem 16 16 '#356F53' '#79D79F'
End-Texture

# Starbuncle hub: independent parcel-and-star emblem.
Begin-Texture 'starbuncle_logistics_hub.png'
Draw-ArsCasing
Rect 9 14 14 11 '#9A684D'; Rect 10 15 12 8 '#C29570'
Rect 15 14 2 11 '#69566F'; Rect 9 18 14 2 '#69566F'
Line 16 7 16 13 '#E7C875' 2; Line 13 10 19 10 '#E7C875' 2
Pixel 12 8 '#B595CC'; Pixel 21 12 '#B595CC'
End-Texture

# Mine core: fewer, larger ore/channel markers.
Begin-Texture 'arcane_mine_core.png'
Draw-Casing '#456D78' '#565067' '#14121D'
Rect 14 6 4 20 '#4C2B83'; Rect 12 9 8 14 '#6C3DB4'; Rect 14 11 4 10 '#A76BE5'
Rect 7 8 4 4 '#C68735'; Rect 21 8 4 4 '#96A1A7'; Rect 7 20 4 4 '#D1B43F'; Rect 21 20 4 4 '#3AB99D'
End-Texture

Begin-Texture 'arcane_mine_core_top.png'
Draw-Casing '#8B5BC0' '#5C5369' '#18131F'
Line 16 6 16 26 '#8D6DB1' 3; Line 6 16 26 16 '#8D6DB1' 3
Line 8 8 24 24 '#5F477A' 2; Line 24 8 8 24 '#5F477A' 2
Draw-Gem 16 16 '#703AA0' '#C278EE'
End-Texture

Begin-Texture 'arcane_mine_core_bottom.png'
Draw-Casing '#36BFC0' '#5C5369' '#15111D'
Rect 9 9 14 14 '#2B1C3B'; Rect 12 12 8 8 '#51306B'; Draw-Gem 16 16 '#70419A' '#BA73DF'
Line 5 16 11 16 '#49D4D0' 2; Line 21 16 27 16 '#49D4D0' 2; Line 16 5 16 11 '#B69A45' 2; Line 16 21 16 27 '#B69A45' 2
End-Texture

# Enchanted archwood charcoal block: coal-block structure with restrained source seams.
Begin-Texture 'enchanted_archwood_charcoal_block.png'
Rect 0 0 32 32 '#0B0910'; Rect 2 2 28 28 '#17141C'
Rect 3 3 11 9 '#24202A'; Rect 16 3 13 9 '#1D1922'; Rect 3 14 8 15 '#1D1922'; Rect 13 14 16 7 '#27222C'; Rect 13 23 16 6 '#201B25'
Line 3 13 12 11 '#4A2D59' 2; Line 12 11 16 15 '#8A4A9F' 2; Line 16 15 28 13 '#4A2D59' 2
Line 11 15 11 28 '#553060' 2; Line 12 22 28 22 '#3D2948' 2
Pixel 8 7 '#C99A3B'; Pixel 22 8 '#9C4CC0'; Pixel 6 21 '#805093'; Pixel 23 25 '#D0A13D'
End-Texture

# Local source-jar atlas is kept as a clean 32px fallback/particle texture.
Begin-Texture 'arcane_source_jar.png' '#00000000'
Rect 4 2 24 4 '#6C4B7C'; Rect 6 6 20 22 '#32223F'; Rect 8 8 16 16 '#4E2871'
Rect 9 15 14 8 '#7D3CB0'; Rect 10 17 12 5 '#B75DE0'; Rect 12 18 8 3 '#D998F2'
Rect 4 28 24 3 '#6C4B7C'; Rect 7 5 3 22 '#8A67A0'; Rect 22 5 3 22 '#493854'
End-Texture

# Order pedestal: a restrained stone body and large parchment order emblem.
Begin-Texture 'arcane_order_pedestal_stone.png' '#635D69'
Rect 0 0 32 2 '#958D9A'
Rect 0 2 2 28 '#817988'
Rect 0 30 32 2 '#413A49'
Rect 30 2 2 28 '#413A49'
End-Texture

Begin-Texture 'arcane_order_pedestal_trim.png' '#644481'
Rect 0 0 32 3 '#9B76B5'
Rect 0 27 32 5 '#402D54'
Rect 0 23 32 3 '#C3A36B'
End-Texture

Begin-Texture 'arcane_order_pedestal_order.png' '#635D69'
Rect 0 0 3 32 '#817988'
Rect 29 0 3 32 '#413A49'
Rect 6 3 21 27 '#413A49'
Rect 5 2 21 27 '#C3A36B'
Rect 7 4 17 23 '#E8D6AC'
Rect 5 2 21 3 '#F4E5C4'
Rect 5 26 21 3 '#C3A36B'
Rect 10 9 11 2 '#80664D'
Rect 10 14 11 2 '#80664D'
Rect 10 19 6 2 '#80664D'
Rect 19 20 5 5 '#644481'
Rect 20 20 3 2 '#9B76B5'
End-Texture

Begin-Texture 'arcane_order_pedestal_top.png' '#644481'
Rect 2 2 28 28 '#9B76B5'
Rect 4 4 24 24 '#413A49'
Rect 6 6 20 20 '#635D69'
Rect 11 11 10 10 '#C3A36B'
Rect 13 13 6 6 '#644481'
End-Texture

Write-Output "Generated selected independent 32x32 textures in $textureRoot"
