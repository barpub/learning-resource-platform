$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$scriptDir = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    Join-Path (Get-Location) 'scripts'
} else {
    $PSScriptRoot
}
$root = Resolve-Path (Join-Path $scriptDir '..')
$out = Join-Path $root 'docs\er-diagram.png'

$width = 3600
$height = 2400
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

function Draw-Text($text, $rect, $font, $brush, $align = 'Near') {
    $format = New-Object System.Drawing.StringFormat
    if ($align -eq 'Center') {
        $format.Alignment = [System.Drawing.StringAlignment]::Center
        $format.LineAlignment = [System.Drawing.StringAlignment]::Center
    } else {
        $format.Alignment = [System.Drawing.StringAlignment]::Near
        $format.LineAlignment = [System.Drawing.StringAlignment]::Center
        $format.Trimming = [System.Drawing.StringTrimming]::EllipsisCharacter
    }
    $graphics.DrawString($text, $font, $brush, $rect, $format)
    $format.Dispose()
}

function Center-Point($name) {
    $t = $script:tables[$name]
    return [System.Drawing.PointF]::new([float]($t.X + $t.W / 2), [float]($t.Y + $t.H / 2))
}

function Edge-Point($name, $side) {
    $t = $script:tables[$name]
    if ($side -eq 'L') { return [System.Drawing.PointF]::new([float]$t.X, [float]($t.Y + $t.H / 2)) }
    if ($side -eq 'R') { return [System.Drawing.PointF]::new([float]($t.X + $t.W), [float]($t.Y + $t.H / 2)) }
    if ($side -eq 'T') { return [System.Drawing.PointF]::new([float]($t.X + $t.W / 2), [float]$t.Y) }
    if ($side -eq 'B') { return [System.Drawing.PointF]::new([float]($t.X + $t.W / 2), [float]($t.Y + $t.H)) }
    return Center-Point $name
}

function Draw-Relation($fromTable, $fromSide, $toTable, $toSide, $label, $offsetX = 0, $offsetY = 0) {
    $from = Edge-Point $fromTable $fromSide
    $to = Edge-Point $toTable $toSide
    $pen = New-Pen '#53657d' 3
    $graphics.DrawLine($pen, $from, $to)

    $font = New-Font 24 ([System.Drawing.FontStyle]::Bold)
    $brush = New-Brush '#1f2937'
    $labelFont = New-Font 22
    $labelBrush = New-Brush '#334155'
    $labelBg = New-Brush '#ffffff'

    $oneRect = [System.Drawing.RectangleF]::new([float]($from.X - 18), [float]($from.Y - 28), [float]36, [float]36)
    $manyRect = [System.Drawing.RectangleF]::new([float]($to.X - 18), [float]($to.Y - 28), [float]36, [float]36)
    $graphics.FillRectangle($labelBg, $oneRect)
    $graphics.FillRectangle($labelBg, $manyRect)
    Draw-Text '1' $oneRect $font $brush 'Center'
    Draw-Text 'N' $manyRect $font $brush 'Center'

    $mx = (($from.X + $to.X) / 2) + $offsetX
    $my = (($from.Y + $to.Y) / 2) + $offsetY
    $labelRect = [System.Drawing.RectangleF]::new([float]($mx - 180), [float]($my - 22), [float]360, [float]44)
    $graphics.FillRectangle($labelBg, $labelRect)
    Draw-Text $label $labelRect $labelFont $labelBrush 'Center'

    $pen.Dispose()
    $font.Dispose()
    $brush.Dispose()
    $labelFont.Dispose()
    $labelBrush.Dispose()
    $labelBg.Dispose()
}

function Draw-RelationLine($fromTable, $fromSide, $toTable, $toSide) {
    $from = Edge-Point $fromTable $fromSide
    $to = Edge-Point $toTable $toSide
    $pen = New-Pen '#53657d' 3
    $graphics.DrawLine($pen, $from, $to)
    $pen.Dispose()
}

function Draw-RelationMarks($fromTable, $fromSide, $toTable, $toSide, $label, $offsetX = 0, $offsetY = 0) {
    $from = Edge-Point $fromTable $fromSide
    $to = Edge-Point $toTable $toSide
    $font = New-Font 24 ([System.Drawing.FontStyle]::Bold)
    $brush = New-Brush '#1f2937'
    $labelFont = New-Font 22
    $labelBrush = New-Brush '#334155'
    $labelBg = New-Brush '#ffffff'

    $oneRect = [System.Drawing.RectangleF]::new([float]($from.X - 18), [float]($from.Y - 28), [float]36, [float]36)
    $manyRect = [System.Drawing.RectangleF]::new([float]($to.X - 18), [float]($to.Y - 28), [float]36, [float]36)
    $graphics.FillRectangle($labelBg, $oneRect)
    $graphics.FillRectangle($labelBg, $manyRect)
    Draw-Text '1' $oneRect $font $brush 'Center'
    Draw-Text 'N' $manyRect $font $brush 'Center'

    $mx = (($from.X + $to.X) / 2) + $offsetX
    $my = (($from.Y + $to.Y) / 2) + $offsetY
    $labelRect = [System.Drawing.RectangleF]::new([float]($mx - 180), [float]($my - 22), [float]360, [float]44)
    $graphics.FillRectangle($labelBg, $labelRect)
    Draw-Text $label $labelRect $labelFont $labelBrush 'Center'

    $font.Dispose()
    $brush.Dispose()
    $labelFont.Dispose()
    $labelBrush.Dispose()
    $labelBg.Dispose()
}

function Draw-SelfRelation($name, $side, $label) {
    $t = $script:tables[$name]
    $pen = New-Pen '#53657d' 3
    $pen.DashStyle = [System.Drawing.Drawing2D.DashStyle]::Dash
    $font = New-Font 22
    $brush = New-Brush '#334155'
    $bg = New-Brush '#ffffff'

    if ($side -eq 'R') {
        $x = $t.X + $t.W
        $y = $t.Y + 92
        $rect = [System.Drawing.RectangleF]::new([float]$x, [float]$y, [float]170, [float]128)
        $graphics.DrawArc($pen, $rect, 270, 180)
        $labelRect = [System.Drawing.RectangleF]::new([float]($x + 84), [float]($y + 36), [float]170, [float]38)
    } else {
        $x = $t.X + 90
        $y = $t.Y - 96
        $rect = [System.Drawing.RectangleF]::new([float]$x, [float]$y, [float]230, [float]150)
        $graphics.DrawArc($pen, $rect, 180, 180)
        $labelRect = [System.Drawing.RectangleF]::new([float]($x + 20), [float]($y - 8), [float]190, [float]38)
    }
    $graphics.FillRectangle($bg, $labelRect)
    Draw-Text $label $labelRect $font $brush 'Center'

    $pen.Dispose()
    $font.Dispose()
    $brush.Dispose()
    $bg.Dispose()
}

function Draw-Table($name, $title, $x, $y, $w, $rows) {
    $headerH = 68
    $rowH = 38
    $h = $headerH + $rows.Count * $rowH
    $script:tables[$name] = @{ X = [float]$x; Y = [float]$y; W = [float]$w; H = [float]$h }

    $shadow = New-Brush '#dbe8f4'
    $header = New-Brush '#1f5f9f'
    $fill = New-Brush '#ffffff'
    $alt = New-Brush '#f8fbff'
    $keyFill = New-Brush '#edf7f6'
    $border = New-Pen '#1f5f9f' 3
    $grid = New-Pen '#d3e0ed' 1
    $titleBrush = New-Brush '#ffffff'
    $textBrush = New-Brush '#0f172a'
    $mutedBrush = New-Brush '#475569'
    $titleFont = New-Font 28 ([System.Drawing.FontStyle]::Bold)
    $rowFont = New-Font 20
    $tagFont = New-Font 18 ([System.Drawing.FontStyle]::Bold)

    $graphics.FillRectangle($shadow, $x + 10, $y + 12, $w, $h)
    $graphics.FillRectangle($fill, $x, $y, $w, $h)
    $graphics.FillRectangle($header, $x, $y, $w, $headerH)
    $graphics.DrawRectangle($border, $x, $y, $w, $h)
    Draw-Text $title ([System.Drawing.RectangleF]::new([float]($x + 18), [float]$y, [float]($w - 36), [float]$headerH)) $titleFont $titleBrush

    for ($i = 0; $i -lt $rows.Count; $i++) {
        $row = $rows[$i]
        $ry = $y + $headerH + $i * $rowH
        $bg = if ($row.Tag -match 'PK|FK') { $keyFill } elseif ($i % 2 -eq 0) { $alt } else { $fill }
        $graphics.FillRectangle($bg, $x, $ry, $w, $rowH)
        $graphics.DrawLine($grid, $x, $ry, $x + $w, $ry)

        $tagColor = if ($row.Tag -match 'PK') { '#0f766e' } elseif ($row.Tag -match 'FK') { '#7c3aed' } else { '#64748b' }
        $tagBrush = New-Brush $tagColor
        Draw-Text $row.Tag ([System.Drawing.RectangleF]::new([float]($x + 14), [float]$ry, [float]84, [float]$rowH)) $tagFont $tagBrush 'Center'
        Draw-Text $row.Field ([System.Drawing.RectangleF]::new([float]($x + 104), [float]$ry, [float]190, [float]$rowH)) $rowFont $textBrush
        Draw-Text $row.Type ([System.Drawing.RectangleF]::new([float]($x + 302), [float]$ry, [float]135, [float]$rowH)) $rowFont $mutedBrush
        Draw-Text $row.Comment ([System.Drawing.RectangleF]::new([float]($x + 446), [float]$ry, [float]($w - 460), [float]$rowH)) $rowFont $textBrush
        $tagBrush.Dispose()
    }

    $graphics.DrawLine($border, $x + 96, $y + $headerH, $x + 96, $y + $h)
    $graphics.DrawLine($border, $x + 294, $y + $headerH, $x + 294, $y + $h)
    $graphics.DrawLine($border, $x + 438, $y + $headerH, $x + 438, $y + $h)

    $shadow.Dispose()
    $header.Dispose()
    $fill.Dispose()
    $alt.Dispose()
    $keyFill.Dispose()
    $border.Dispose()
    $grid.Dispose()
    $titleBrush.Dispose()
    $textBrush.Dispose()
    $mutedBrush.Dispose()
    $titleFont.Dispose()
    $rowFont.Dispose()
    $tagFont.Dispose()
}

function Row($tag, $field, $type, $comment) {
    return @{ Tag = $tag; Field = $field; Type = $type; Comment = $comment }
}

$background = New-Brush '#ffffff'
$graphics.FillRectangle($background, 0, 0, $width, $height)
$background.Dispose()

$script:tables = @{}

$titleBrush = New-Brush '#0f172a'
$subBrush = New-Brush '#475569'
$titleFont = New-Font 58 ([System.Drawing.FontStyle]::Bold)
$subFont = New-Font 28
Draw-Text '学习资源共享平台系统 ER 图' ([System.Drawing.RectangleF]::new([float]0, [float]24, [float]$width, [float]72)) $titleFont $titleBrush 'Center'
Draw-Text '实体：用户、分类、资源、评论、收藏、下载记录；标注 PK、FK 和 1:N 关系' ([System.Drawing.RectangleF]::new([float]0, [float]98, [float]$width, [float]44)) $subFont $subBrush 'Center'

$userRows = @(
    Row 'PK' 'id' 'BIGINT' '用户ID'
    Row 'UK' 'username' 'VARCHAR' '用户名'
    Row '' 'password' 'VARCHAR' '加密密码'
    Row 'UK' 'email' 'VARCHAR' '邮箱'
    Row '' 'nickname' 'VARCHAR' '昵称'
    Row '' 'avatar' 'VARCHAR' '头像URL'
    Row '' 'role' 'VARCHAR' '角色'
    Row '' 'status' 'TINYINT' '状态'
    Row '' 'create_time' 'DATETIME' '创建时间'
    Row '' 'update_time' 'DATETIME' '更新时间'
)

$categoryRows = @(
    Row 'PK' 'id' 'BIGINT' '分类ID'
    Row '' 'name' 'VARCHAR' '分类名称'
    Row '' 'description' 'VARCHAR' '分类描述'
    Row 'FK' 'parent_id' 'BIGINT' '父分类ID'
    Row '' 'sort_order' 'INT' '排序序号'
    Row '' 'create_time' 'DATETIME' '创建时间'
    Row '' 'update_time' 'DATETIME' '更新时间'
)

$resourceRows = @(
    Row 'PK' 'id' 'BIGINT' '资源ID'
    Row '' 'title' 'VARCHAR' '资源标题'
    Row '' 'description' 'TEXT' '资源描述'
    Row '' 'file_name' 'VARCHAR' '原始文件名'
    Row '' 'file_path' 'VARCHAR' '文件存储路径'
    Row '' 'file_size' 'BIGINT' '文件大小'
    Row '' 'file_type' 'VARCHAR' '文件类型'
    Row '' 'resource_type' 'VARCHAR' 'FILE/FOLDER'
    Row 'FK' 'parent_id' 'BIGINT' '父资源ID'
    Row '' 'sort_order' 'INT' '文件夹内排序'
    Row '' 'relative_path' 'VARCHAR' '相对路径'
    Row '' 'file_count' 'INT' '文件数量'
    Row 'FK' 'category_id' 'BIGINT' '分类ID'
    Row 'FK' 'user_id' 'BIGINT' '上传者ID'
    Row '' 'download_count' 'INT' '下载次数'
    Row '' 'view_count' 'INT' '浏览次数'
    Row '' 'rating' 'DECIMAL' '平均评分'
    Row '' 'rating_count' 'INT' '评分人数'
    Row '' 'status' 'TINYINT' '状态'
    Row '' 'create_time' 'DATETIME' '创建时间'
    Row '' 'update_time' 'DATETIME' '更新时间'
)

$commentRows = @(
    Row 'PK' 'id' 'BIGINT' '评论ID'
    Row 'FK' 'resource_id' 'BIGINT' '资源ID'
    Row 'FK' 'user_id' 'BIGINT' '用户ID'
    Row '' 'content' 'TEXT' '评论内容'
    Row '' 'rating' 'TINYINT' '评分'
    Row 'FK' 'parent_id' 'BIGINT' '父评论ID'
    Row '' 'status' 'TINYINT' '状态'
    Row '' 'create_time' 'DATETIME' '创建时间'
    Row '' 'update_time' 'DATETIME' '更新时间'
)

$favoriteRows = @(
    Row 'PK' 'id' 'BIGINT' '收藏ID'
    Row 'FK' 'user_id' 'BIGINT' '用户ID'
    Row 'FK' 'resource_id' 'BIGINT' '资源ID'
    Row 'UK' 'user_id,resource_id' 'UNIQUE' '用户资源唯一'
    Row '' 'create_time' 'DATETIME' '创建时间'
)

$downloadRows = @(
    Row 'PK' 'id' 'BIGINT' '记录ID'
    Row 'FK' 'user_id' 'BIGINT' '用户ID'
    Row 'FK' 'resource_id' 'BIGINT' '资源ID'
    Row '' 'download_time' 'DATETIME' '下载时间'
    Row '' 'ip_address' 'VARCHAR' 'IP地址'
)

# Reserve table rectangles first so relations can be drawn behind them.
Draw-Table 'user' 'user 用户表' 130 340 690 $userRows
Draw-Table 'resource' 'resource 资源表' 1350 220 850 $resourceRows
Draw-Table 'category' 'category 分类表' 2700 340 700 $categoryRows
Draw-Table 'comment' 'comment 评论表' 130 1340 720 $commentRows
Draw-Table 'favorite' 'favorite 收藏表' 1360 1440 760 $favoriteRows
Draw-Table 'download' 'download_record 下载记录表' 2600 1440 780 $downloadRows

# Draw relation lines first, then redraw tables so lines do not cover field text.
Draw-RelationLine 'user' 'R' 'resource' 'L'
Draw-RelationLine 'category' 'L' 'resource' 'R'
Draw-RelationLine 'resource' 'L' 'comment' 'R'
Draw-RelationLine 'user' 'B' 'comment' 'T'
Draw-RelationLine 'user' 'B' 'favorite' 'L'
Draw-RelationLine 'resource' 'B' 'favorite' 'T'
Draw-RelationLine 'user' 'R' 'download' 'L'
Draw-RelationLine 'resource' 'B' 'download' 'T'

Draw-Table 'user' 'user 用户表' 130 340 690 $userRows
Draw-Table 'resource' 'resource 资源表' 1350 220 850 $resourceRows
Draw-Table 'category' 'category 分类表' 2700 340 700 $categoryRows
Draw-Table 'comment' 'comment 评论表' 130 1340 720 $commentRows
Draw-Table 'favorite' 'favorite 收藏表' 1360 1440 760 $favoriteRows
Draw-Table 'download' 'download_record 下载记录表' 2600 1440 780 $downloadRows

Draw-RelationMarks 'user' 'R' 'resource' 'L' '上传资源' 0 -72
Draw-RelationMarks 'category' 'L' 'resource' 'R' '资源分类' 0 -72
Draw-RelationMarks 'resource' 'L' 'comment' 'R' '资源评论' -60 0
Draw-RelationMarks 'user' 'B' 'comment' 'T' '用户评论' 30 -40
Draw-RelationMarks 'user' 'B' 'favorite' 'L' '用户收藏' -120 16
Draw-RelationMarks 'resource' 'B' 'favorite' 'T' '资源被收藏' 80 -18
Draw-RelationMarks 'user' 'R' 'download' 'L' '用户下载' 40 90
Draw-RelationMarks 'resource' 'B' 'download' 'T' '资源被下载' 70 18
Draw-SelfRelation 'category' 'T' '父子分类'
Draw-SelfRelation 'resource' 'R' '父子资源'
Draw-SelfRelation 'comment' 'R' '父子评论'

$legendBg = New-Brush '#f8fafc'
$legendBorder = New-Pen '#cbd5e1' 2
$legendBrush = New-Brush '#334155'
$legendFont = New-Font 24
$legend = [System.Drawing.RectangleF]::new([float]130, [float]2230, [float]1660, [float]54)
$graphics.FillRectangle($legendBg, $legend)
$graphics.DrawRectangle($legendBorder, 130, 2230, 1660, 54)
Draw-Text '说明：PK 表示主键，FK 表示外键字段，UK 表示唯一约束；关系线上 1 与 N 表示一对多关系。' $legend $legendFont $legendBrush 'Center'

$bitmap.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)

$graphics.Dispose()
$bitmap.Dispose()
$titleBrush.Dispose()
$subBrush.Dispose()
$titleFont.Dispose()
$subFont.Dispose()
$legendBg.Dispose()
$legendBorder.Dispose()
$legendBrush.Dispose()
$legendFont.Dispose()

Write-Output $out
