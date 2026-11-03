package com.sealease.backend.reporting.export;

import com.sealease.backend.reporting.catalog.ColumnType;
import com.sealease.backend.reporting.catalog.ReportResult;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JRDesignField;
import net.sf.jasperreports.engine.design.JRDesignImage;
import net.sf.jasperreports.engine.design.JRDesignLine;
import net.sf.jasperreports.engine.design.JRDesignSection;
import net.sf.jasperreports.engine.design.JRDesignStaticText;
import net.sf.jasperreports.engine.design.JRDesignStyle;
import net.sf.jasperreports.engine.design.JRDesignTextField;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.type.EvaluationTimeEnum;
import net.sf.jasperreports.engine.type.HorizontalImageAlignEnum;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import net.sf.jasperreports.engine.type.OrientationEnum;
import net.sf.jasperreports.engine.type.ScaleImageEnum;
import net.sf.jasperreports.engine.type.TextAdjustEnum;
import net.sf.jasperreports.engine.type.WhenNoDataTypeEnum;

import java.awt.Color;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a report as a landscape A4 PDF with JasperReports: the company header, title, period, filters and
 * generation time; a table whose columns are laid out from the report's columns; the totals; and on every page a
 * confidentiality footer with page numbers. The layout is built in code (a fixed .jrxml cannot know the columns).
 * Fonts are the embedded DejaVu fonts, so ₹ prints.
 */
final class TablePdfRenderer {

	private static final int PAGE_WIDTH = 842;
	private static final int PAGE_HEIGHT = 595;
	private static final int MARGIN = 28;
	private static final int WIDTH = PAGE_WIDTH - 2 * MARGIN;
	private static final Color INK = new Color(0x0B, 0x1F, 0x2A);
	private static final Color MUTED = new Color(0x5B, 0x67, 0x70);
	private static final Color BRAND = new Color(0x1F, 0x62, 0xC9);
	private static final Color BAND = new Color(0xE8, 0xEE, 0xF8);

	private TablePdfRenderer() {
	}

	static byte[] render(ReportResult result, ExportContext context, ZoneId zone) {
		List<ReportResult.Column> columns = result.columns();
		int[] widths = widths(columns);
		float fontSize = columns.size() > 10 ? 6.5f : columns.size() > 7 ? 7.5f : 8.5f;
		try {
			JasperDesign design = new JasperDesign();
			design.setName("report");
			design.setPageWidth(PAGE_WIDTH);
			design.setPageHeight(PAGE_HEIGHT);
			design.setOrientation(OrientationEnum.LANDSCAPE);
			design.setColumnWidth(WIDTH);
			design.setLeftMargin(MARGIN);
			design.setRightMargin(MARGIN);
			design.setTopMargin(MARGIN);
			design.setBottomMargin(22);
			design.setWhenNoDataType(WhenNoDataTypeEnum.ALL_SECTIONS_NO_DETAIL);
			JRDesignStyle base = new JRDesignStyle();
			base.setName("Base");
			base.setDefault(true);
			base.setFontName("DejaVu Sans");
			base.setFontSize(fontSize);
			base.setForecolor(INK);
			design.addStyle(base);
			for (int i = 0; i < columns.size(); i++) {
				JRDesignField field = new JRDesignField();
				field.setName("c" + i);
				field.setValueClass(String.class);
				design.addField(field);
			}
			JRDesignField shade = new JRDesignField();
			shade.setName("shade");
			shade.setValueClass(Boolean.class);
			design.addField(shade);

			design.setTitle(title(context, result));
			design.setColumnHeader(header(columns, widths, fontSize));
			((JRDesignSection) design.getDetailSection()).addBand(detail(columns, widths, fontSize));
			design.setSummary(summary(result, columns, widths, fontSize, zone));
			design.setPageFooter(footer(context));

			JasperReport compiled = JasperCompileManager.compileReport(design);
			List<Map<String, ?>> rows = new ArrayList<>();
			int n = 0;
			String currency = result.totals().size() == 1 ? result.totals().getFirst().currency() : "INR";
			for (List<Object> values : result.rows()) {
				Map<String, Object> row = new HashMap<>();
				for (int i = 0; i < columns.size(); i++) {
					row.put("c" + i, Cells.display(columns.get(i).type(), values.get(i), currency, zone));
				}
				row.put("shade", n++ % 2 == 1);
				rows.add(row);
			}
			JasperPrint print = JasperFillManager.fillReport(compiled, new HashMap<>(), new JRMapCollectionDataSource(rows));
			return JasperExportManager.exportReportToPdf(print);
		}
		catch (JRException ex) {
			throw new IllegalStateException("Could not render report " + result.key(), ex);
		}
	}

	/** Column widths in points: by type, scaled to the page. */
	static int[] widths(List<ReportResult.Column> columns) {
		double[] weights = new double[columns.size()];
		double sum = 0;
		for (int i = 0; i < columns.size(); i++) {
			weights[i] = switch (columns.get(i).type()) {
				case MONEY -> 1.15;
				case DATETIME -> 1.2;
				case DATE, MONTH -> 0.9;
				case NUMBER, PERCENT -> 0.7;
				case STATUS -> 1.0;
				case TEXT -> 1.35;
			};
			sum += weights[i];
		}
		int[] widths = new int[columns.size()];
		int used = 0;
		for (int i = 0; i < columns.size(); i++) {
			widths[i] = (int) Math.floor(WIDTH * weights[i] / sum);
			used += widths[i];
		}
		if (widths.length > 0) {
			widths[widths.length - 1] += WIDTH - used;
		}
		return widths;
	}

	private static JRDesignBand title(ExportContext context, ReportResult result) {
		JRDesignBand band = new JRDesignBand();
		band.setHeight(92);
		JRDesignImage logo = new JRDesignImage(null);
		logo.setX(0);
		logo.setY(0);
		logo.setWidth(40);
		logo.setHeight(32);
		logo.setScaleImage(ScaleImageEnum.RETAIN_SHAPE);
		logo.setHorizontalImageAlign(HorizontalImageAlignEnum.LEFT);
		logo.setExpression(new JRDesignExpression("\"reports/logo.png\""));
		band.addElement(logo);
		band.addElement(staticText("SEALEASE", 48, 8, 200, 16, 12f, true, new Color(0x0F, 0x1F, 0x38), HorizontalTextAlignEnum.LEFT));
		band.addElement(staticText("Generated " + context.generatedAt() + " by " + context.generatedBy(), WIDTH - 360, 10, 360,
				12, 7.5f, false, MUTED, HorizontalTextAlignEnum.RIGHT));
		band.addElement(staticText(context.title(), 0, 38, WIDTH, 20, 15f, true, INK, HorizontalTextAlignEnum.LEFT));
		String line = "Period: " + context.period() + "   ·   " + result.rows().size() + " rows   ·   Filters: "
				+ (context.filters().isEmpty() ? "none" : String.join("; ", context.filters()));
		JRDesignStaticText filters = staticText(line, 0, 60, WIDTH, 12, 7.5f, false, MUTED, HorizontalTextAlignEnum.LEFT);
		band.addElement(filters);
		JRDesignLine rule = new JRDesignLine();
		rule.setX(0);
		rule.setY(80);
		rule.setWidth(WIDTH);
		rule.setHeight(1);
		rule.setForecolor(BRAND);
		rule.getLinePen().setLineWidth(1.25f);
		band.addElement(rule);
		return band;
	}

	private static JRDesignBand header(List<ReportResult.Column> columns, int[] widths, float fontSize) {
		JRDesignBand band = new JRDesignBand();
		band.setHeight(20);
		int x = 0;
		for (int i = 0; i < columns.size(); i++) {
			JRDesignStaticText label = staticText(columns.get(i).label(), x, 0, widths[i], 20, fontSize, true, INK,
					columns.get(i).type().numeric() ? HorizontalTextAlignEnum.RIGHT : HorizontalTextAlignEnum.LEFT);
			label.setMode(net.sf.jasperreports.engine.type.ModeEnum.OPAQUE);
			label.setBackcolor(BAND);
			label.getLineBox().setLeftPadding(3);
			label.getLineBox().setRightPadding(3);
			label.getLineBox().setTopPadding(4);
			band.addElement(label);
			x += widths[i];
		}
		return band;
	}

	private static JRDesignBand detail(List<ReportResult.Column> columns, int[] widths, float fontSize) {
		JRDesignBand band = new JRDesignBand();
		int height = Math.round(fontSize * 2.1f);
		band.setHeight(height);
		int x = 0;
		for (int i = 0; i < columns.size(); i++) {
			JRDesignTextField field = new JRDesignTextField();
			field.setX(x);
			field.setY(0);
			field.setWidth(widths[i]);
			field.setHeight(height);
			field.setFontSize(fontSize);
			field.setBlankWhenNull(true);
			field.setTextAdjust(TextAdjustEnum.STRETCH_HEIGHT);
			field.setHorizontalTextAlign(columns.get(i).type().numeric() ? HorizontalTextAlignEnum.RIGHT : HorizontalTextAlignEnum.LEFT);
			field.getLineBox().setLeftPadding(3);
			field.getLineBox().setRightPadding(3);
			field.getLineBox().setTopPadding(2);
			field.getLineBox().setBottomPadding(2);
			field.getLineBox().getBottomPen().setLineWidth(0.25f);
			field.getLineBox().getBottomPen().setLineColor(new Color(0xD7, 0xDD, 0xE4));
			field.setExpression(new JRDesignExpression("$F{c" + i + "}"));
			band.addElement(field);
			x += widths[i];
		}
		return band;
	}

	private static JRDesignBand summary(ReportResult result, List<ReportResult.Column> columns, int[] widths, float fontSize,
			ZoneId zone) {
		JRDesignBand band = new JRDesignBand();
		int y = 6;
		if (result.rows().isEmpty()) {
			band.addElement(staticText("Nothing matches this period and these filters.", 0, y, WIDTH, 14, 8.5f, false, MUTED,
					HorizontalTextAlignEnum.LEFT));
			y += 18;
		}
		for (ReportResult.Totals totals : result.totals()) {
			int x = 0;
			boolean labelled = false;
			for (int i = 0; i < columns.size(); i++) {
				Object value = totals.values().get(columns.get(i).key());
				if (!labelled && i == 0) {
					band.addElement(staticText(result.totals().size() > 1 ? "Total (" + totals.currency() + ")" : "Total", x, y,
							widths[i], 14, fontSize, true, INK, HorizontalTextAlignEnum.LEFT));
					labelled = true;
				}
				else if (value != null) {
					JRDesignStaticText total = staticText(Cells.display(columns.get(i).type(), value, totals.currency(), zone),
							x, y, widths[i], 14, fontSize, true, INK, HorizontalTextAlignEnum.RIGHT);
					total.getLineBox().getTopPen().setLineWidth(0.75f);
					total.getLineBox().setRightPadding(3);
					band.addElement(total);
				}
				x += widths[i];
			}
			y += 18;
		}
		if (result.note() != null) {
			JRDesignStaticText note = staticText(result.note(), 0, y + 4, WIDTH, 22, 7f, false, MUTED, HorizontalTextAlignEnum.LEFT);
			band.addElement(note);
			y += 30;
		}
		band.setHeight(Math.max(y, 1));
		return band;
	}

	private static JRDesignBand footer(ExportContext context) {
		JRDesignBand band = new JRDesignBand();
		band.setHeight(16);
		band.addElement(staticText("Confidential · SeaLease internal report · " + context.title(), 0, 4, 500, 10, 6.5f,
				false, MUTED, HorizontalTextAlignEnum.LEFT));
		JRDesignTextField page = new JRDesignTextField();
		page.setX(WIDTH - 120);
		page.setY(4);
		page.setWidth(95);
		page.setHeight(10);
		page.setFontSize(6.5f);
		page.setForecolor(MUTED);
		page.setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
		page.setExpression(new JRDesignExpression("\"Page \" + $V{PAGE_NUMBER} + \" of\""));
		band.addElement(page);
		JRDesignTextField total = new JRDesignTextField();
		total.setX(WIDTH - 24);
		total.setY(4);
		total.setWidth(24);
		total.setHeight(10);
		total.setFontSize(6.5f);
		total.setForecolor(MUTED);
		total.setEvaluationTime(EvaluationTimeEnum.REPORT);
		total.getLineBox().setLeftPadding(3);
		total.setExpression(new JRDesignExpression("$V{PAGE_NUMBER}"));
		band.addElement(total);
		return band;
	}

	private static JRDesignStaticText staticText(String text, int x, int y, int width, int height, float size, boolean bold,
			Color color, HorizontalTextAlignEnum align) {
		JRDesignStaticText element = new JRDesignStaticText();
		element.setText(text == null ? "" : text);
		element.setX(x);
		element.setY(y);
		element.setWidth(width);
		element.setHeight(height);
		element.setFontSize(size);
		element.setBold(bold);
		element.setForecolor(color);
		element.setHorizontalTextAlign(align);
		return element;
	}

}
