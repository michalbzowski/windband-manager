package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.query.report.MonthlyReport;
import pl.michalbzowski.windband.application.query.report.ReportQueryService;

import java.time.YearMonth;

@Controller
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportPageController {

    private final ReportQueryService reportQueryService;

    @GetMapping
    public String reportsPage(Model model) {
        model.addAttribute("currentMonth", YearMonth.now());
        return "reports/list";
    }

    @GetMapping("/generate")
    public String generateReport(@RequestParam int year, @RequestParam int month, Model model) {
        YearMonth ym = YearMonth.of(year, month);
        MonthlyReport report = reportQueryService.generateMonthlyReport(ym);
        model.addAttribute("report", report);
        return "reports/view";
    }
}
