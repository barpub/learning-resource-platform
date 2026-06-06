$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$scriptDir = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    Join-Path (Get-Location) 'scripts'
} else {
    $PSScriptRoot
}
$root = Resolve-Path (Join-Path $scriptDir '..')
$out = Join-Path $root 'docs\standard-er-diagram.png'
$compatOut = Join-Path $root 'docs\er-diagram.png'

$width = 2200
$height = 1500
$bitmap = New-Object System.Drawing.Bitmap($width, $height)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit

function New-Brush($html) {
    return New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml($html))
}

function New-Pen($html, $size = 2) {
    return New-Object System.Drawing.Pen([System.Drawing.ColorTranslator]::FromHtml($html), $size)
}

function New-Font($size, $style = [System.Drawing.FontStyle]::Regular) {
    return New-Object System.Drawing.Font('Microsoft YaHei', $size, $style, [System.Drawing.GraphicsUnit]::Pixel)
}

function Shape($x, $y, $w, $h) {
    return @{ X = [float]$x; Y = [float]$y; W = [float]$w; H = [float]$h }
}

function Rect($shape) {
    return [System.Drawing.RectangleF]::new([float]$shape.X, [float]$shape.Y, [float]$shape.W, [float]$shape.H)
}

function Center($shape) {
    return [System.Drawing.PointF]::new([float]($shape.X + $shape.W / 2), [float]($shape.Y + $shape.H / 2))
}

function Anchor($shape, $side) {
    if ($side -eq 'L25') { return [System.Drawing.PointF]::new([float]$shape.X, [float]($shape.Y + $shape.H * 0.25)) }
    if ($side -eq 'L50') { return [System.Drawing.PointF]::new([float]$shape.X, [float]($shape.Y + $shape.H * 0.50)) }
    if ($side -eq 'L75') { return [System.Drawing.PointF]::new([float]$shape.X, [float]($shape.Y + $shape.H * 0.75)) }
    if ($side -eq 'R25') { return [System.Drawing.PointF]::new([float]($shape.X + $shape.W), [float]($shape.Y + $shape.H * 0.25)) }
    if ($side -eq 'R50') { return [System.Drawing.PointF]::new([float]($shape.X + $shape.W), [float]($shape.Y + $shape.H * 0.50)) }
    if ($side -eq 'R75') { return [System.Drawing.PointF]::new([float]($shape.X + $shape.W), [float]($shape.Y + $shape.H * 0.75)) }
    if ($side -eq 'T25') { return [System.Drawing.PointF]::new([float]($shape.X + $shape.W * 0.25), [float]$shape.Y) }
    if ($side -eq 'T50') { return [System.Drawing.PointF]::new([float]($shape.X + $shape.W * 0.50), [float]$shape.Y) }
    if ($side -eq 'T75') { return [System.Drawing.PointF]::new([float]($shape.X + $shape.W * 0.75), [float]$shape.Y) }
    if ($side -eq 'B25') { return [System.Drawing.PointF]::new([float]($shape.X + $shape.W * 0.25), [float]($shape.Y + $shape.H)) }
    if ($side -eq 'B50') { return [System.Drawing.PointF]::new([float]($shape.X + $shape.W * 0.50), [float]($shape.Y + $shape.H)) }
    if ($side -eq 'B75') { return [System.Drawing.PointF]::new([float]($shape.X + $shape.W * 0.75), [float]($shape.Y + $shape.H)) }
    if ($side -eq 'L') { return [System.Drawing.PointF]::new([float]$shape.X, [float]($shape.Y + $shape.H / 2)) }
    if ($side -eq 'R') { return [System.Drawing.PointF]::new([float]($shape.X + $shape.W), [float]($shape.Y + $shape.H / 2)) }
    if ($side -eq 'T') { return [System.Drawing.PointF]::new([float]($shape.X + $shape.W / 2), [float]$shape.Y) }
    if ($side -eq 'B') { return [System.Drawing.PointF]::new([float]($shape.X + $shape.W / 2), [float]($shape.Y + $shape.H)) }
    return Center $shape
}

function Draw-CenteredText($text, $rect, $font, $brush) {
    $format = New-Object System.Drawing.StringFormat
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center
    $graphics.DrawString($text, $font, $brush, $rect, $format)
    $format.Dispose()
}

function Draw-Line($from, $fromSide, $to, $toSide) {
    $pen = New-Pen '#1f2937' 2
    $graphics.DrawLine($pen, (Anchor $from $fromSide), (Anchor $to $toSide))
    $pen.Dispose()
}

function Draw-Entity($shape, $text) {
    $fill = New-Brush '#eaf2ff'
    $border = New-Pen '#111827' 2
    $font = New-Font 25 ([System.Drawing.FontStyle]::Bold)
    $brush = New-Brush '#111827'
    $graphics.FillRectangle($fill, (Rect $shape))
    $graphics.DrawRectangle($border, $shape.X, $shape.Y, $shape.W, $shape.H)
    Draw-CenteredText $text (Rect $shape) $font $brush
    $fill.Dispose()
    $border.Dispose()
    $font.Dispose()
    $brush.Dispose()
}

function Draw-Attribute($shape, $text, $pk = $false) {
    $fill = New-Brush '#ffffff'
    $border = New-Pen '#111827' 2
    $style = if ($pk) { [System.Drawing.FontStyle]::Underline } else { [System.Drawing.FontStyle]::Regular }
    $font = New-Font 20 $style
    $brush = New-Brush '#111827'
    $graphics.FillEllipse($fill, (Rect $shape))
    $graphics.DrawEllipse($border, (Rect $shape))
    Draw-CenteredText $text (Rect $shape) $font $brush
    $fill.Dispose()
    $border.Dispose()
    $font.Dispose()
    $brush.Dispose()
}

function Draw-Relation($shape, $text) {
    $fill = New-Brush '#fff7ed'
    $border = New-Pen '#111827' 2
    $font = New-Font 20
    $brush = New-Brush '#111827'
    $points = @(
        [System.Drawing.PointF]::new([float]($shape.X + $shape.W / 2), [float]$shape.Y),
        [System.Drawing.PointF]::new([float]($shape.X + $shape.W), [float]($shape.Y + $shape.H / 2)),
        [System.Drawing.PointF]::new([float]($shape.X + $shape.W / 2), [float]($shape.Y + $shape.H)),
        [System.Drawing.PointF]::new([float]$shape.X, [float]($shape.Y + $shape.H / 2))
    )
    $graphics.FillPolygon($fill, $points)
    $graphics.DrawPolygon($border, $points)
    Draw-CenteredText $text (Rect $shape) $font $brush
    $fill.Dispose()
    $border.Dispose()
    $font.Dispose()
    $brush.Dispose()
}

function Draw-Cardinality($text, $x, $y) {
    $font = New-Font 22 ([System.Drawing.FontStyle]::Bold)
    $brush = New-Brush '#c62828'
    Draw-CenteredText $text ([System.Drawing.RectangleF]::new([float]$x, [float]$y, [float]34, [float]28)) $font $brush
    $font.Dispose()
    $brush.Dispose()
}

$bg = New-Brush '#ffffff'
$graphics.FillRectangle($bg, 0, 0, $width, $height)
$bg.Dispose()

$titleFont = New-Font 32 ([System.Drawing.FontStyle]::Bold)
$titleBrush = New-Brush '#000000'
Draw-CenteredText '图4-2 学习资源共享平台系统E-R图' ([System.Drawing.RectangleF]::new([float]0, [float]32, [float]$width, [float]45)) $titleFont $titleBrush
$titleFont.Dispose()
$titleBrush.Dispose()

$entity = @{
    user = Shape 210 520 230 95
    resource = Shape 1000 500 245 95
    category = Shape 1700 365 230 95
    comment = Shape 1000 1030 245 95
}

$relation = @{
    upload = Shape 650 520 120 82
    collect = Shape 650 710 120 82
    download = Shape 650 910 120 82
    publish = Shape 650 1110 120 82
    belong = Shape 1425 445 120 82
    comment = Shape 1115 800 120 82
}

$attrs = @(
    @{ Target = $entity.user; Side = 'TL'; Shape = Shape 90 365 150 55; Text = '用户ID'; Pk = $true },
    @{ Target = $entity.user; Side = 'T'; Shape = Shape 285 340 135 55; Text = '用户名'; Pk = $false },
    @{ Target = $entity.user; Side = 'TR'; Shape = Shape 440 370 120 55; Text = '角色'; Pk = $false },
    @{ Target = $entity.user; Side = 'L'; Shape = Shape 60 535 120 55; Text = '密码'; Pk = $false },
    @{ Target = $entity.user; Side = 'BL'; Shape = Shape 100 705 120 55; Text = '邮箱'; Pk = $false },
    @{ Target = $entity.user; Side = 'B'; Shape = Shape 300 710 120 55; Text = '昵称'; Pk = $false },

    @{ Target = $entity.resource; Side = 'TL'; Shape = Shape 880 330 150 55; Text = '资源ID'; Pk = $true },
    @{ Target = $entity.resource; Side = 'T'; Shape = Shape 1070 310 140 55; Text = '资源标题'; Pk = $false },
    @{ Target = $entity.resource; Side = 'TR'; Shape = Shape 1270 360 130 55; Text = '文件名'; Pk = $false },
    @{ Target = $entity.resource; Side = 'R'; Shape = Shape 1310 520 135 55; Text = '文件类型'; Pk = $false },
    @{ Target = $entity.resource; Side = 'BR'; Shape = Shape 1260 660 150 55; Text = '资源类型'; Pk = $false },
    @{ Target = $entity.resource; Side = 'B'; Shape = Shape 1080 690 145 55; Text = '文件路径'; Pk = $false },
    @{ Target = $entity.resource; Side = 'BL'; Shape = Shape 875 650 150 55; Text = '创建时间'; Pk = $false },

    @{ Target = $entity.category; Side = 'TL'; Shape = Shape 1590 230 150 55; Text = '分类ID'; Pk = $true },
    @{ Target = $entity.category; Side = 'T'; Shape = Shape 1780 220 150 55; Text = '分类名称'; Pk = $false },
    @{ Target = $entity.category; Side = 'R'; Shape = Shape 1950 385 150 55; Text = '分类描述'; Pk = $false },
    @{ Target = $entity.category; Side = 'B'; Shape = Shape 1765 550 140 55; Text = '排序序号'; Pk = $false },

    @{ Target = $entity.comment; Side = 'TL'; Shape = Shape 870 925 150 55; Text = '评论ID'; Pk = $true },
    @{ Target = $entity.comment; Side = 'T'; Shape = Shape 1120 900 150 55; Text = '评论内容'; Pk = $false },
    @{ Target = $entity.comment; Side = 'R'; Shape = Shape 1275 1040 110 55; Text = '评分'; Pk = $false },
    @{ Target = $entity.comment; Side = 'BR'; Shape = Shape 1180 1195 110 55; Text = '状态'; Pk = $false },
    @{ Target = $entity.comment; Side = 'B'; Shape = Shape 950 1195 150 55; Text = '创建时间'; Pk = $false },

    @{ Target = $relation.collect; Side = 'T'; Shape = Shape 560 650 150 55; Text = '收藏时间'; Pk = $false },
    @{ Target = $relation.download; Side = 'T'; Shape = Shape 500 840 150 55; Text = '下载时间'; Pk = $false },
    @{ Target = $relation.download; Side = 'B'; Shape = Shape 500 1055 135 55; Text = 'IP地址'; Pk = $false }
)

function AttributeTargetSide($side) {
    if ($side -eq 'TL') { return 'T' }
    if ($side -eq 'TR') { return 'T' }
    if ($side -eq 'BL') { return 'B' }
    if ($side -eq 'BR') { return 'B' }
    return $side
}

function AttributeShapeSide($side) {
    if ($side -eq 'L') { return 'R' }
    if ($side -eq 'R') { return 'L' }
    if ($side -eq 'T' -or $side -eq 'TL' -or $side -eq 'TR') { return 'B' }
    if ($side -eq 'B' -or $side -eq 'BL' -or $side -eq 'BR') { return 'T' }
    return 'C'
}

# Relationship connectors, drawn first like the sample.
Draw-Line $entity.user 'R25' $relation.upload 'L'
Draw-Line $relation.upload 'R' $entity.resource 'L25'
Draw-Line $entity.user 'R50' $relation.collect 'L'
Draw-Line $relation.collect 'R' $entity.resource 'L50'
Draw-Line $entity.user 'R75' $relation.download 'L'
Draw-Line $relation.download 'R' $entity.resource 'L75'
Draw-Line $entity.user 'B75' $relation.publish 'L'
Draw-Line $relation.publish 'R' $entity.comment 'L50'
Draw-Line $entity.resource 'R' $relation.belong 'L'
Draw-Line $relation.belong 'R' $entity.category 'L'
Draw-Line $entity.resource 'B' $relation.comment 'T'
Draw-Line $relation.comment 'B' $entity.comment 'T'

foreach ($a in $attrs) {
    Draw-Line $a.Target (AttributeTargetSide $a.Side) $a.Shape (AttributeShapeSide $a.Side)
}

# Shapes on top of connector lines.
foreach ($a in $attrs) {
    Draw-Attribute $a.Shape $a.Text $a.Pk
}

Draw-Relation $relation.upload '上传'
Draw-Relation $relation.collect '收藏'
Draw-Relation $relation.download '下载'
Draw-Relation $relation.publish '发表'
Draw-Relation $relation.belong '归属'
Draw-Relation $relation.comment '拥有'

Draw-Entity $entity.user '用户'
Draw-Entity $entity.resource '资源'
Draw-Entity $entity.category '分类'
Draw-Entity $entity.comment '评论'

# Cardinalities near relationship lines.
Draw-Cardinality '1' 470 530
Draw-Cardinality 'N' 950 525
Draw-Cardinality 'N' 465 705
Draw-Cardinality 'N' 820 690
Draw-Cardinality 'N' 465 910
Draw-Cardinality 'N' 815 875
Draw-Cardinality '1' 470 1080
Draw-Cardinality 'N' 925 1070
Draw-Cardinality 'N' 1270 465
Draw-Cardinality '1' 1650 418
Draw-Cardinality '1' 1130 760
Draw-Cardinality 'N' 1130 980

# Legend copied from the sample style.
$legendX = 250
$legendY = 1245
$legendW = 700
$legendH = 170
$legendBg = New-Brush '#fbfbfb'
$legendBorder = New-Pen '#d1d5db' 2
$legendFont = New-Font 20
$legendTitleFont = New-Font 22 ([System.Drawing.FontStyle]::Bold)
$legendBrush = New-Brush '#111827'
$graphics.FillRectangle($legendBg, $legendX, $legendY, $legendW, $legendH)
$graphics.DrawRectangle($legendBorder, $legendX, $legendY, $legendW, $legendH)
Draw-CenteredText '图例' ([System.Drawing.RectangleF]::new([float]($legendX + 20), [float]($legendY + 12), [float]80, [float]34)) $legendTitleFont $legendBrush

$miniEntity = Shape ($legendX + 45) ($legendY + 62) 86 40
$miniAttr = Shape ($legendX + 45) ($legendY + 116) 86 38
$miniRel = Shape ($legendX + 340) ($legendY + 58) 58 52
Draw-Entity $miniEntity ''
Draw-Attribute $miniAttr ''
Draw-Relation $miniRel ''
Draw-CenteredText '实体（矩形）' ([System.Drawing.RectangleF]::new([float]($legendX + 160), [float]($legendY + 62), [float]150, [float]40)) $legendFont $legendBrush
Draw-CenteredText '属性（椭圆）' ([System.Drawing.RectangleF]::new([float]($legendX + 160), [float]($legendY + 116), [float]150, [float]38)) $legendFont $legendBrush
Draw-CenteredText '关系（菱形）' ([System.Drawing.RectangleF]::new([float]($legendX + 420), [float]($legendY + 66), [float]150, [float]40)) $legendFont $legendBrush

$cardFont = New-Font 22 ([System.Drawing.FontStyle]::Bold)
$cardBrush = New-Brush '#c62828'
Draw-CenteredText '1' ([System.Drawing.RectangleF]::new([float]($legendX + 420), [float]($legendY + 118), [float]28, [float]30)) $cardFont $cardBrush
Draw-CenteredText '一端基数' ([System.Drawing.RectangleF]::new([float]($legendX + 455), [float]($legendY + 118), [float]100, [float]30)) $legendFont $legendBrush
Draw-CenteredText 'N' ([System.Drawing.RectangleF]::new([float]($legendX + 560), [float]($legendY + 118), [float]30, [float]30)) $cardFont $cardBrush
Draw-CenteredText '多端基数' ([System.Drawing.RectangleF]::new([float]($legendX + 594), [float]($legendY + 118), [float]100, [float]30)) $legendFont $legendBrush

$bitmap.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$bitmap.Save($compatOut, [System.Drawing.Imaging.ImageFormat]::Png)

$graphics.Dispose()
$bitmap.Dispose()
$legendBg.Dispose()
$legendBorder.Dispose()
$legendFont.Dispose()
$legendTitleFont.Dispose()
$legendBrush.Dispose()
$cardFont.Dispose()
$cardBrush.Dispose()

Write-Output $out
Write-Output $compatOut
