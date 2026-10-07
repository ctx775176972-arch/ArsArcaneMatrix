param(
    [Parameter(Mandatory=$true)][string]$Template,
    [Parameter(Mandatory=$true)][string]$Output
)
# Exact template recolor: change the pickaxe glyph, never regenerate the rim.
Add-Type -AssemblyName System.Drawing
$tabletImage = [System.Drawing.Bitmap]::new($Template)
try {
    if ($tabletImage.Width -ne 16 -or $tabletImage.Height -ne 16) {
        throw 'Expected the original 16x16 ritual tablet template.'
    }
    for ($glyphY = 4; $glyphY -le 11; $glyphY++) {
        for ($glyphX = 4; $glyphX -le 11; $glyphX++) {
            $glyphColor = $tabletImage.GetPixel($glyphX, $glyphY)
            if ($glyphColor.A -gt 0 -and $glyphColor.G -gt $glyphColor.R + 10) {
                $glyphShade = [Math]::Clamp([int]($glyphColor.G * 0.2), 12, 38)
                $tabletImage.SetPixel($glyphX, $glyphY,
                    [System.Drawing.Color]::FromArgb($glyphColor.A, $glyphShade, $glyphShade, $glyphShade))
            }
        }
    }
    $tabletImage.Save($Output, [System.Drawing.Imaging.ImageFormat]::Png)
} finally { $tabletImage.Dispose() }
