# Packages original project artwork into the fixed Milestone 19B libGDX atlas contract.
# Source images are retained outside the APK under docs/art-source for reproducible packaging.
Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

$source = Join-Path $PSScriptRoot '../docs/art-source'
$destination = Join-Path $PSScriptRoot '../android/app/src/main/assets/arena'
$atlasBitmap = New-Object Drawing.Bitmap 2048, 2048, ([Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [Drawing.Graphics]::FromImage($atlasBitmap)
$graphics.Clear([Drawing.Color]::Transparent)
$graphics.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceCopy
$graphics.CompositingQuality = [Drawing.Drawing2D.CompositingQuality]::HighQuality
$graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::Half
$graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::HighQuality

$descriptor = New-Object Text.StringBuilder
[void]$descriptor.AppendLine("combat.png`nsize: 2048, 2048`nformat: RGBA8888`nfilter: Linear, Linear`nrepeat: none")

function Add-Region($name, $x, $y, $width, $height, $index = -1) {
    [void]$descriptor.AppendLine("$name`n  rotate: false`n  xy: $x, $y`n  size: $width, $height`n  orig: $width, $height`n  offset: 0, 0`n  index: $index")
}

function Draw-SourceRegion($image, $sourceRectangle, $destinationRectangle) {
    $graphics.DrawImage(
        $image,
        $destinationRectangle,
        $sourceRectangle.X,
        $sourceRectangle.Y,
        $sourceRectangle.Width,
        $sourceRectangle.Height,
        [Drawing.GraphicsUnit]::Pixel
    )
}

function Feather-CellEdges($bitmap) {
    $rectangle = [Drawing.Rectangle]::new(0, 0, $bitmap.Width, $bitmap.Height)
    $data = $bitmap.LockBits($rectangle, [Drawing.Imaging.ImageLockMode]::ReadWrite,
        [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $bytes = New-Object byte[] ([math]::Abs($data.Stride) * $bitmap.Height)
        [Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)
        for ($y = 0; $y -lt $bitmap.Height; $y++) {
            for ($x = 0; $x -lt $bitmap.Width; $x++) {
                $edge = [math]::Min([math]::Min($x, $bitmap.Width - 1 - $x),
                    [math]::Min($y, $bitmap.Height - 1 - $y))
                if ($edge -lt 8) {
                    $alpha = $y * $data.Stride + $x * 4 + 3
                    $bytes[$alpha] = [byte]($bytes[$alpha] * $edge / 8)
                }
            }
        }
        [Runtime.InteropServices.Marshal]::Copy($bytes, 0, $data.Scan0, $bytes.Length)
    } finally {
        $bitmap.UnlockBits($data)
    }
}

function Draw-FighterSheet($file, $prefix, $firstCell) {
    $image = [Drawing.Image]::FromFile((Join-Path $source $file))
    try {
        if ($image.Width -ne 1024 -or $image.Height -ne 1536) {
            throw "$file must remain 1024 x 1536 (four columns by six rows)."
        }
        $animations = @('idle', 'melee', 'projectile', 'hit', 'ko', 'victory')
        for ($row = 0; $row -lt 6; $row++) {
            for ($frame = 0; $frame -lt 4; $frame++) {
                $cell = $firstCell + $row * 4 + $frame
                $x = ($cell % 8) * 256 + 2
                $y = [math]::Floor($cell / 8) * 256 + 2
                Add-Region "$prefix/$($animations[$row])" $x $y 252 252 $frame
                $cell = New-Object Drawing.Bitmap 256, 256, ([Drawing.Imaging.PixelFormat]::Format32bppArgb)
                $cellGraphics = [Drawing.Graphics]::FromImage($cell)
                $cellGraphics.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceCopy
                $cellGraphics.DrawImage($image, 0, 0,
                    [Drawing.Rectangle]::new($frame * 256, $row * 256, 256, 256),
                    [Drawing.GraphicsUnit]::Pixel)
                $cellGraphics.Dispose()
                # Fade only the outer eight source pixels, then add an eight-pixel atlas inset.
                # This removes hard generated-sheet seams without borrowing a neighbouring pose.
                Feather-CellEdges $cell
                Draw-SourceRegion $cell ([Drawing.Rectangle]::new(0, 0, 256, 256)) `
                    ([Drawing.Rectangle]::new($x + 8, $y + 8, 236, 236))
                $cell.Dispose()
            }
        }
    } finally {
        $image.Dispose()
    }
}

Draw-FighterSheet 'blue-robot-sheet-source.png' 'blue' 0
Draw-FighterSheet 'red-robot-sheet-source.png' 'red' 24

$background = [Drawing.Image]::FromFile((Join-Path $source 'arena-background-source.png'))
try {
    Add-Region 'arena/background' 2 1538 1000 500
    Draw-SourceRegion $background ([Drawing.Rectangle]::new(0, 0, $background.Width, $background.Height)) `
        ([Drawing.Rectangle]::new(2, 1538, 1000, 500))
} finally {
    $background.Dispose()
}

$platform = [Drawing.Image]::FromFile((Join-Path $source 'arena-platform-source.png'))
try {
    Add-Region 'arena/platform' 1010 1538 1000 120
    # The source already uses transparent margins; the full image preserves its authored edge glow.
    Draw-SourceRegion $platform ([Drawing.Rectangle]::new(0, 0, $platform.Width, $platform.Height)) `
        ([Drawing.Rectangle]::new(1010, 1538, 1000, 120))
} finally {
    $platform.Dispose()
}

$effects = [Drawing.Image]::FromFile((Join-Path $source 'combat-effects-source.png'))
try {
    $effectNames = @('projectile', 'glow', 'impact', 'burst', 'spark', 'victory')
    for ($index = 0; $index -lt $effectNames.Count; $index++) {
        $column = $index % 3
        $row = [math]::Floor($index / 3)
        $x = 1010 + $column * 132
        $y = 1670 + $row * 132
        Add-Region "fx/$($effectNames[$index])" $x $y 128 128
        Draw-SourceRegion $effects ([Drawing.Rectangle]::new($column * 512, $row * 512, 512, 512)) `
            ([Drawing.Rectangle]::new($x, $y, 128, 128))
    }
} finally {
    $effects.Dispose()
}

# Small deterministic utility sprites share the production palette and avoid extra source files.
Add-Region 'fx/shadow' 1410 1670 128 48
$shadowPath = New-Object Drawing.Drawing2D.GraphicsPath
$shadowPath.AddEllipse(1412, 1674, 124, 40)
$shadowBrush = New-Object Drawing.Drawing2D.PathGradientBrush $shadowPath
$shadowBrush.CenterColor = [Drawing.Color]::FromArgb(145, 2, 10, 24)
$shadowBrush.SurroundColors = @([Drawing.Color]::Transparent)
$graphics.FillPath($shadowBrush, $shadowPath)
$shadowBrush.Dispose()
$shadowPath.Dispose()

Add-Region 'fx/paused' 1550 1670 64 64
$pauseGlow = New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(190, 18, 37, 64))
$pauseBar = New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(255, 118, 242, 255))
$graphics.FillEllipse($pauseGlow, 1552, 1672, 60, 60)
$graphics.FillRectangle($pauseBar, 1568, 1686, 9, 34)
$graphics.FillRectangle($pauseBar, 1587, 1686, 9, 34)
$pauseGlow.Dispose()
$pauseBar.Dispose()

$graphics.Dispose()
$atlasBitmap.Save((Join-Path $destination 'combat.png'), [Drawing.Imaging.ImageFormat]::Png)
$atlasBitmap.Dispose()
[IO.File]::WriteAllText((Join-Path $destination 'combat.atlas'), $descriptor.ToString())
