package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.query.report.MonthlyReport;
import pl.michalbzowski.windband.application.query.report.ReportQueryService;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportQueryService reportQueryService;

    @GetMapping("/monthly")
    public ResponseEntity<MonthlyReport> getMonthlyReport(
            @RequestParam int year,
            @RequestParam int month) {
        YearMonth ym = YearMonth.of(year, month);
        MonthlyReport report = reportQueryService.generateMonthlyReport(ym);
        return ResponseEntity.ok(report);
    }
}
