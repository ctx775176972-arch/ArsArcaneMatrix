param(
    [Parameter(Mandatory=$true)][string]$InputImage,
    [Parameter(Mandatory=$true)][string]$OutputImage
)
# Convert generated artwork to the established inventory asset resolution.
Add-Type -AssemblyName System.Drawing
$sourceSprite = [System.Drawing.Image]::FromFile($InputImage)
$targetSprite = [System.Drawing.Bitmap]::new(32, 32)
$spriteGraphics = [System.Drawing.Graphics]::FromImage($targetSprite)
try {
    $spriteGraphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $spriteGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $spriteGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $spriteGraphics.DrawImage($sourceSprite, [System.Drawing.Rectangle]::new(0,0,32,32),
        0,0,$sourceSprite.Width,$sourceSprite.Height,[System.Drawing.GraphicsUnit]::Pixel)
    $targetSprite.Save($OutputImage, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $spriteGraphics.Dispose()
    $targetSprite.Dispose()
    $sourceSprite.Dispose()
}
