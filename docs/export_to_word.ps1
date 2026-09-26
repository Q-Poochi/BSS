$mdPath = "C:\Project\BSS\docs\BSS_Agent_Skills_Installation_Report.md"
$docxPath = "C:\Project\BSS\docs\BSS_Agent_Skills_Installation_Report.docx"

Write-Host "Khoi dong tien trinh Microsoft Word..."
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0

try {
    $content = Get-Content -Path $mdPath -Raw -Encoding utf8
    $doc = $word.Documents.Add()
    $selection = $word.Selection
    
    $doc.PageSetup.PaperSize = 7 # wdPaperA4
    $doc.PageSetup.TopMargin = 72
    $doc.PageSetup.BottomMargin = 72
    $doc.PageSetup.LeftMargin = 72
    $doc.PageSetup.RightMargin = 72
    
    $lines = $content -split "`r?`n"
    $inTable = $false
    $tableRows = @()
    
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        
        if ($line.Trim().StartsWith("|") -and $line.Trim().EndsWith("|")) {
            if ($line -match '^\|\s*[-:]+\s*\|') {
                continue
            }
            $tableRows += ,($line.Trim().Trim('|') -split '\|' | ForEach-Object { $_.Trim() })
            $inTable = $true
            continue
        } else {
            if ($inTable -and $tableRows.Count -gt 0) {
                $numRows = $tableRows.Count
                $numCols = $tableRows[0].Count
                $range = $selection.Range
                $table = $doc.Tables.Add($range, $numRows, $numCols)
                $table.Borders.Enable = $true
                $table.Range.Font.Name = "Times New Roman"
                $table.Range.Font.Size = 10
                
                for ($r = 0; $r -lt $numRows; $r++) {
                    for ($c = 0; $c -lt $numCols; $c++) {
                        if ($c -lt $tableRows[$r].Count) {
                            $cell = $table.Cell($r + 1, $c + 1)
                            $cell.Range.Text = $tableRows[$r][$c]
                            if ($r -eq 0) {
                                $cell.Range.Font.Bold = $true
                                $cell.Shading.BackgroundPatternColor = 14540253
                            }
                        }
                    }
                }
                $selection.Start = $table.Range.End
                $selection.InsertParagraphAfter()
                $selection.Start = $selection.End
                $tableRows = @()
                $inTable = $false
            }
        }
        
        if ($line.StartsWith("# ")) {
            $selection.Font.Name = "Times New Roman"
            $selection.Font.Size = 18
            $selection.Font.Bold = $true
            $selection.Font.ColorIndex = 9 # wdBlue
            $selection.TypeText($line.Substring(2).Trim())
            $selection.TypeParagraph()
        } elseif ($line.StartsWith("## ")) {
            $selection.Font.Name = "Times New Roman"
            $selection.Font.Size = 14
            $selection.Font.Bold = $true
            $selection.Font.ColorIndex = 1 # wdBlack
            $selection.TypeText($line.Substring(3).Trim())
            $selection.TypeParagraph()
        } elseif ($line.StartsWith("### ")) {
            $selection.Font.Name = "Times New Roman"
            $selection.Font.Size = 12
            $selection.Font.Bold = $true
            $selection.Font.ColorIndex = 1
            $selection.TypeText($line.Substring(4).Trim())
            $selection.TypeParagraph()
        } elseif ($line.Trim() -eq "---") {
            $selection.TypeParagraph()
        } elseif ($line.StartsWith('```')) {
            $codeBlock = ""
            $i++
            while ($i -lt $lines.Count -and !$lines[$i].StartsWith('```')) {
                $codeBlock += $lines[$i] + "`n"
                $i++
            }
            $selection.Font.Name = "Consolas"
            $selection.Font.Size = 9.5
            $selection.Font.Bold = $false
            $selection.Font.ColorIndex = 1
            $selection.TypeText($codeBlock)
            $selection.TypeParagraph()
        } else {
            $cleanLine = $line -replace '\*\*(.*?)\*\*', '$1' -replace '\*(.*?)\*', '$1' -replace '`([^`]+)`', '$1'
            $selection.Font.Name = "Times New Roman"
            $selection.Font.Size = 11
            $selection.Font.Bold = $false
            $selection.Font.ColorIndex = 1
            $selection.TypeText($cleanLine)
            $selection.TypeParagraph()
        }
    }
    
    if (Test-Path $docxPath) { Remove-Item $docxPath -Force }
    $doc.SaveAs([ref]$docxPath, [ref]16)
    Write-Host "XUAT THANH CONG: $docxPath"
    $doc.Close([ref]0)
} finally {
    $word.Quit()
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null
    [System.GC]::Collect()
    [System.GC]::WaitForPendingFinalizers()
}