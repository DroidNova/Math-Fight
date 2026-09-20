# Original temporary vector-style artwork, rasterized offline with Windows System.Drawing.
# Run from repository root. No external assets or dependencies. Not production artwork.
Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$destination = Join-Path $PSScriptRoot '../android/app/src/main/assets/arena'
$bitmap = New-Object Drawing.Bitmap 2048,2048
$g = [Drawing.Graphics]::FromImage($bitmap)
$g.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([Drawing.Color]::Transparent)
$atlas = New-Object Text.StringBuilder
[void]$atlas.AppendLine("combat.png`nsize: 2048, 2048`nformat: RGBA8888`nfilter: Linear, Linear`nrepeat: none")
function Region($name, $x, $y, $w, $h, $index = -1) {
    [void]$atlas.AppendLine("$name`n  rotate: false`n  xy: $x, $y`n  size: $w, $h`n  orig: $w, $h`n  offset: 0, 0`n  index: $index")
}
function Brush($color) { New-Object Drawing.SolidBrush ([Drawing.ColorTranslator]::FromHtml($color)) }
function Rect($color, [single]$x,[single]$y,[single]$w,[single]$h) {
    $b = Brush $color; $g.FillRectangle($b,$x,$y,$w,$h); $b.Dispose()
}
function Oval($color, [single]$x,[single]$y,[single]$w,[single]$h) {
    $b = Brush $color; $g.FillEllipse($b,$x,$y,$w,$h); $b.Dispose()
}
function Line($color,[single]$width,[single]$x1,[single]$y1,[single]$x2,[single]$y2) {
    $p = New-Object Drawing.Pen ([Drawing.ColorTranslator]::FromHtml($color)), $width
    $p.StartCap = 'Round'; $p.EndCap = 'Round'; $g.DrawLine($p,$x1,$y1,$x2,$y2); $p.Dispose()
}
# 48 robot cells, transparent 256 square; all poses share foot anchor (128,240).
$states = @('idle','melee','projectile','hit','ko','victory')
for ($side=0; $side -lt 2; $side++) {
    $body = if ($side -eq 0) { '#21ABEF' } else { '#FA6847' }
    $light = if ($side -eq 0) { '#9AFAFF' } else { '#FFE29A' }
    $prefix = if ($side -eq 0) { 'blue' } else { 'red' }
    for ($state=0; $state -lt 6; $state++) {
        for ($frame=0; $frame -lt 4; $frame++) {
            $cell = $side*24 + $state*4 + $frame
            $x=($cell%8)*256; $y=[math]::Floor($cell/8)*256
            Region "$prefix/$($states[$state])" ($x+2) ($y+2) 252 252 $frame
            $saved=$g.Save(); $g.TranslateTransform($x+2,$y+2)
            if ($side -eq 1) { $g.TranslateTransform(252,0); $g.ScaleTransform(-1,1) }
            if ($state -eq 4) {
                $g.TranslateTransform(126,180)
                $g.RotateTransform(-($frame/3)*78)
                $g.ScaleTransform((1-$frame*0.06),(1-$frame*0.06))
                $g.TranslateTransform(-126,-180)
            }
            $bob = if ($state -eq 0) { @(0,-2,0,2)[$frame] } else { 0 }
            $g.TranslateTransform(0,$bob)
            $outline='#10263D'
            Line $outline 22 108 188 101 230
            Line $outline 22 145 188 153 230
            Rect $outline 84 224 34 16; Rect $outline 140 224 34 16
            Rect $light 89 227 25 7; Rect $light 144 227 25 7
            # Friendly rounded capsule body and bright chest core.
            Oval $outline 77 97 100 110; Rect $outline 77 128 100 48
            Oval $body 84 103 86 98; Rect $body 84 128 86 45
            Oval $outline 103 129 48 48; Oval $light 110 136 34 34
            Oval '#FFFFFF' 116 139 10 10
            $frontX=181; $frontY=150; $backY=159
            if ($state -eq 1) { $frontX=@(171,203,222,183)[$frame]; $frontY=@(145,126,130,151)[$frame] }
            if ($state -eq 2) { $frontX=@(168,183,196,181)[$frame]; $frontY=139 }
            if ($state -eq 5) { $frontX=181; $frontY=@(135,98,61,59)[$frame]; $backY=$frontY }
            Line $outline 25 83 126 62 $backY; Line $body 15 83 126 62 $backY
            Line $outline 27 170 126 $frontX $frontY; Line $body 17 170 126 $frontX $frontY
            Oval $outline ($frontX-15) ($frontY-14) 31 30; Oval $light ($frontX-10) ($frontY-9) 21 20
            # Head, antenna, inset visor and two expressive eyes.
            Line $outline 7 127 50 127 34; Oval $light 120 26 14 14
            Oval $outline 77 48 100 74; Rect $outline 77 78 100 20
            Oval $body 84 54 86 60; Rect $body 84 78 86 17
            Rect $outline 91 70 68 26
            if ($state -eq 4 -and $frame -gt 0) {
                Line '#718392' 4 103 79 114 87; Line '#718392' 4 114 79 103 87
                Line '#718392' 4 135 79 146 87; Line '#718392' 4 146 79 135 87
                Oval '#506477' 110 136 34 34
            } else {
                Rect $light 104 76 11 13; Rect $light 137 76 11 13
                Line $outline 3 118 102 137 102
            }
            $g.Restore($saved)
        }
    }
}
# Two arena layers at native world aspect; quiet upper area keeps silhouettes readable.
Region 'arena/background' 2 1538 1000 500
$saved=$g.Save(); $g.TranslateTransform(2,1538)
for ($band=0; $band -lt 50; $band++) {
    $b = New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(255, (12+$band/5), (24+$band/3), (49+$band/2)))
    $g.FillRectangle($b,0,($band*10),1000,10); $b.Dispose()
}
for ($tower=0; $tower -lt 10; $tower++) {
    $tx=$tower*108-24; $ty=130+($tower%3)*32
    Rect '#203954' $tx $ty 76 (420-$ty)
    Rect '#2A4963' ($tx+5) ($ty+7) 4 (400-$ty)
    for ($window=0; $window -lt 4; $window++) { Rect '#41647C' ($tx+17) ($ty+24+$window*32) 29 4 }
}
Line '#37607C' 5 0 376 1000 376
Line '#6198AA' 2 0 383 1000 383
$g.Restore($saved)
Region 'arena/platform' 1010 1540 1000 120
$saved=$g.Save(); $g.TranslateTransform(1010,1540); $g.SetClip([Drawing.Rectangle]::new(0,0,1000,120))
Rect '#213B52' 0 0 1000 120
Line '#6AE4EE' 5 15 12 985 12
Line '#12273C' 12 0 40 1000 40
for ($panel=0; $panel -lt 10; $panel++) {
    Line '#36566F' 2 ($panel*100) 45 ($panel*100-35) 120
    Rect '#4A93AA' ($panel*100+12) 55 26 5
}
$g.Restore($saved)
Region 'fx/shadow' 1010 1680 128 48
Oval '#102239' 1012 1682 124 44
Region 'fx/glow' 1150 1680 128 128
for ($ring=0; $ring -lt 12; $ring++) {
    $b=New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(8+$ring*2,255,255,255))
    $g.FillEllipse($b,(1152+$ring*4),(1682+$ring*4),(124-$ring*8),(124-$ring*8)); $b.Dispose()
}
Region 'fx/projectile' 1290 1680 64 64
Oval '#B2EDFF' 1293 1683 58 58; Oval '#FFFFFF' 1301 1691 42 42
Region 'fx/impact' 1370 1680 64 64
Line '#FFFFFF' 8 1402 1687 1402 1737
Line '#FFFFFF' 8 1377 1712 1427 1712
Line '#FFFFFF' 5 1385 1695 1419 1729
Line '#FFFFFF' 5 1385 1729 1419 1695
Region 'fx/paused' 1450 1680 64 64
Oval '#10263D' 1452 1682 60 60
Rect '#9AFAFF' 1469 1696 9 32
Rect '#9AFAFF' 1487 1696 9 32
$g.Dispose()
$bitmap.Save((Join-Path $destination 'combat.png'),[Drawing.Imaging.ImageFormat]::Png)
$bitmap.Dispose()
[IO.File]::WriteAllText((Join-Path $destination 'combat.atlas'),$atlas.ToString())
