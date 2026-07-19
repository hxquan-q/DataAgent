/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.util;

import org.apache.commons.lang3.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中文日期时间表达式解析工具类。
 * <p>
 * 将自然语言中的中文日期/时间表达式（如"去年3月"、"近30天"、"本月最后一周"、"今年第12周"等） 解析为具体的日期区间或日期描述，供查询改写、报表生成等场景使用。
 * </p>
 * <p>
 * 解析流程：通过预定义的正则表达式匹配表达式类别，再调用对应的处理方法计算具体日期， 最终拼接为"原始表达式=解析结果"形式的说明文本。
 * </p>
 */
public class DateTimeUtil {

	/** 精确年月日表达式：如 "2024年03月15日" */
	public static final Pattern SPECIFIC_YEAR_MONTH_DAY_PATTERN = Pattern.compile("\\d{4}年\\d{2}月\\d{2}日");

	/** 相对年+月日表达式：如 "去年03月15日" */
	public static final Pattern GENERAL_YEAR_MONTH_DAY_PATTERN = Pattern.compile("(今年|去年|前年|明年|后年)(\\d{2}月\\d{2}日)");

	/** 相对月+日表达式：如 "本月15日" */
	public static final Pattern GENERAL_MONTH_DAY_PATTERN = Pattern.compile("(本月|上月|上上月|下月)(\\d{2}日)");

	/** 相对日表达式：如 "今天"、"昨天"、"上月今天" */
	public static final Pattern GENERAL_DAY_PATTERN = Pattern.compile("(今天|昨天|前天|明天|后天|上月今天|上上月今天)");

	/** 本周第 N 天表达式：如 "本周第3天" */
	public static final Pattern WEEK_DAY_PATTERN = Pattern.compile("本周第(\\d)天");

	/** 相对月最后一天表达式：如 "本月最后一天" */
	public static final Pattern GENERAL_MONTH_LAST_DAY_PATTERN = Pattern.compile("(本月|上月)最后一天");

	/** 相对年月最后一天表达式：如 "今年03月最后一天" */
	public static final Pattern GENERAL_YEAR_MONTH_LAST_DAY_PATTERN = Pattern.compile("(今年)(\\d{2})月最后一天");

	/** 相对周星期表达式：如 "本周星期一"、"上周星期三" */
	public static final Pattern GENERAL_WEEK_SPECIFIC_DAY_PATTERN = Pattern.compile("(本周|上周|上上周|下周|下下周)星期(\\d)");

	/** 精确年月表达式：如 "2024年03月" */
	public static final Pattern SPECIFIC_YEAR_MONTH_PATTERN = Pattern.compile("\\d{4}年\\d{2}月");

	/** 相对年月表达式：如 "去年03月" */
	public static final Pattern GENERAL_YEAR_MONTH_PATTERN = Pattern.compile("(今年|去年|前年|明年|后年)(\\d{2}月)");

	/** 相对月表达式：如 "本月"、"上上月"、"去年本月" */
	public static final Pattern GENERAL_MONTH_PATTERN = Pattern.compile("(本月|上月|上上月|下月|去年本月)");

	/** 精确年表达式：如 "2024年" */
	public static final Pattern SPECIFIC_YEAR_PATTERN = Pattern.compile("(\\d{4})年");

	/** 相对年表达式：如 "今年"、"前年" */
	public static final Pattern GENERAL_YEAR_PATTERN = Pattern.compile("(今年|去年|前年|明年|后年)");

	/** 精确年季度表达式：如 "2024年第1季度" */
	public static final Pattern SPECIFIC_YEAR_QUARTER_PATTERN = Pattern.compile("\\d{4}年第\\d季度");

	/** 相对年季度表达式：如 "去年第1季度" */
	public static final Pattern GENERAL_YEAR_QUARTER_PATTERN = Pattern.compile("(今年|去年|前年|明年|后年)(第\\d季度)");

	/** 相对季度表达式：如 "本季度"、"上季度" */
	public static final Pattern GENERAL_QUARTER_PATTERN = Pattern.compile("(本季度|上季度|下季度|去年本季度)");

	/** 相对周表达式：如 "本周"、"上上周" */
	public static final Pattern GENERAL_WEEK_PATTERN = Pattern.compile("(本周|上周|上上周|下周|下下周)");

	/** 精确年周表达式：如 "2024年第12周" */
	public static final Pattern SPECIFIC_YEAR_WEEK_PATTERN = Pattern.compile("(\\d{4})年第(\\d{2})周");

	/** 精确年月周表达式：如 "2024年03月第2周" */
	public static final Pattern SPECIFIC_YEAR_MONTH_WEEK_PATTERN = Pattern.compile("(\\d{4})年(\\d{2})月第(\\d)周");

	/** 相对年周表达式：如 "今年第12周" */
	public static final Pattern GENERAL_YEAR_WEEK_PATTERN = Pattern.compile("(今年|去年|前年|明年|后年)第(\\d{2})周");

	/** 相对月周表达式：如 "本月第2周" */
	public static final Pattern GENERAL_MONTH_WEEK_PATTERN = Pattern.compile("(本月|上月)第(\\d)周");

	/** 相对年月周表达式：如 "去年03月第2周" */
	public static final Pattern GENERAL_YEAR_MONTH_WEEK_PATTERN = Pattern.compile("(今年|去年|前年|明年|后年)(\\d{2})月第(\\d)周");

	/** 精确年月最后一周表达式：如 "2024年03月最后一周" */
	public static final Pattern SPECIFIC_YEAR_MONTH_LAST_WEEK_PATTERN = Pattern.compile("(\\d{4})年(\\d{2})月最后一周");

	/** 相对月最后一周表达式：如 "本月最后一周" */
	public static final Pattern GENERAL_MONTH_LAST_WEEK_PATTERN = Pattern.compile("(本月|上月|上上月)最后一周");

	/** 精确年月完整周表达式：如 "2024年03月第2个完整周" */
	public static final Pattern SPECIFIC_YEAR_MONTH_COMPLETE_WEEK_PATTERN = Pattern
		.compile("(\\d{4})年(\\d{2})月第(\\d)个完整周");

	/** 相对年完整周表达式：如 "今年第12个完整周" */
	public static final Pattern GENERAL_YEAR_COMPLETE_WEEK_PATTERN = Pattern.compile("(今年|去年|前年|明年|后年)第(\\d{2})个完整周");

	/** 精确年完整周表达式：如 "2024年第12个完整周" */
	public static final Pattern SPECIFIC_YEAR_COMPLETE_WEEK_PATTERN = Pattern.compile("(\\d{4})年第(\\d{2})个完整周");

	/** 相对年月完整周表达式：如 "今年03月第2个完整周" */
	public static final Pattern GENERAL_YEAR_MONTH_COMPLETE_WEEK_PATTERN = Pattern
		.compile("(今年|去年|前年|明年|后年)(\\d{2})月第(\\d)个完整周");

	/** 相对月完整周表达式：如 "本月第2个完整周" */
	public static final Pattern GENERAL_MONTH_COMPLETE_WEEK_PATTERN = Pattern.compile("(本月|上月)第(\\d)个完整周");

	/** 相对月最后一个完整周表达式：如 "本月最后一个完整周" */
	public static final Pattern GENERAL_MONTH_LAST_COMPLETE_WEEK_PATTERN = Pattern.compile("(本月|上月|上上月)最后一个完整周");

	/** 近 N 年表达式：如 "近3年" */
	public static final Pattern RECENT_N_YEAR_PATTERN = Pattern.compile("近(\\d+)年");

	/** 近 N 个月表达式：如 "近6个月" */
	public static final Pattern RECENT_N_MONTH_PATTERN = Pattern.compile("近(\\d+)个月");

	/** 近 N 周表达式：如 "近4周" */
	public static final Pattern RECENT_N_WEEK_PATTERN = Pattern.compile("近(\\d+)周");

	/** 近 N 天表达式：如 "近30天" */
	public static final Pattern RECENT_N_DAY_PATTERN = Pattern.compile("近(\\d+)天");

	/** 近 N 个完整年表达式：如 "近3个完整年" */
	public static final Pattern RECENT_N_COMPLETE_YEAR_PATTERN = Pattern.compile("近(\\d+)个完整年");

	/** 近 N 个完整季度表达式：如 "近2个完整季度" */
	public static final Pattern RECENT_N_COMPLETE_QUARTER_PATTERN = Pattern.compile("近(\\d+)个完整季度");

	/** 近 N 个完整月表达式：如 "近6个完整月" */
	public static final Pattern RECENT_N_COMPLETE_MONTH_PATTERN = Pattern.compile("近(\\d+)个完整月");

	/** 近 N 个完整周表达式：如 "近4个完整周" */
	public static final Pattern RECENT_N_COMPLETE_WEEK_PATTERN = Pattern.compile("近(\\d+)个完整周");

	/** 不包含今天的近 N 天表达式：如 "不包含今天的近7天" */
	public static final Pattern RECENT_N_DAY_WITHOUT_TODAY_PATTERN = Pattern.compile("不包含今天的近(\\d+)天");

	/** 包含当前季度的近 N 个季度表达式：如 "包含当前季度的近4个季度" */
	public static final Pattern RECENT_N_QUARTER_WITH_CURRENT_PATTERN = Pattern.compile("包含当前季度的近(\\d+)个季度");

	/** 精确年上下半年表达式：如 "2024年上半年" */
	public static final Pattern SPECIFIC_YEAR_HALF_YEAR_PATTERN = Pattern.compile("(\\d{4})年(上|下)半年");

	/** 相对年上下半年表达式：如 "今年下半年" */
	public static final Pattern GENERAL_YEAR_HALF_YEAR_PATTERN = Pattern.compile("(今年|去年|前年|明年|后年)(上|下)半年");

	/** 上下半年表达式：如 "上半年"、"下半年" */
	public static final Pattern HALF_YEAR_PATTERN = Pattern.compile("(上|下)半年");

	/**
	 * 构造日期时间说明，包含当前日期信息和各表达式的解析结果。
	 * <p>
	 * 返回文本格式：第一行为"今天是…"的当前日期说明，后续逐行列出"原始表达式=解析结果"。
	 * </p>
	 * @param expressions 待解析的中文日期表达式列表
	 * @return 包含当前日期说明和各表达式解析结果的文本
	 */
	public static String buildDateTimeComment(List<String> expressions) {
		LocalDate now = LocalDate.now();
		// 获取当前年、月、日
		int year = now.getYear();
		int month = now.getMonthValue();
		int day = now.getDayOfMonth();

		// 获取当前年份对应的季度
		int quarter = now.get(IsoFields.QUARTER_OF_YEAR);

		String todayComment = String.format("今天是%d年%02d月%02d日，是%d年的第%d季度", year, month, day, year, quarter);

		// 解析每个表达式得到说明列表
		List<String> dateTimeCommentList = buildDateExpressions(expressions, now);

		// 拼接当前日期说明与各表达式解析结果
		StringBuilder finalExpression = new StringBuilder();
		finalExpression.append(todayComment).append("\n");
		finalExpression.append("需要计算的时间是：\n");
		dateTimeCommentList.forEach(comment -> finalExpression.append(comment).append("\n"));
		return finalExpression.toString();
	}

	/**
	 * 将多个中文日期表达式逐一解析为"原始表达式=解析结果"形式的说明文本。
	 * <p>
	 * 内部按预定义的正则表达式依次匹配各类表达式，并调用对应的解析方法计算具体日期。
	 * </p>
	 * @param expressions 待解析的中文日期表达式列表
	 * @param now 当前日期，作为相对计算的基准
	 * @return 解析结果说明列表，每项格式为"原始表达式=解析结果"
	 */
	public static List<String> buildDateExpressions(List<String> expressions, LocalDate now) {
		List<String> dateTimeCommentList = new ArrayList<>();
		for (String expression : expressions) {
			Matcher specificYearMonthDayMatcher = SPECIFIC_YEAR_MONTH_DAY_PATTERN.matcher(expression);
			if (specificYearMonthDayMatcher.matches()) {
				dateTimeCommentList.add(expression + "=" + expression);
				continue;
			}

			Matcher generalYearMonthDayMatcher = GENERAL_YEAR_MONTH_DAY_PATTERN.matcher(expression);
			if (generalYearMonthDayMatcher.matches()) {
				String yearEx = generalYearMonthDayMatcher.group(1);
				String comment = getYearEx(now, yearEx, false) + generalYearMonthDayMatcher.group(2);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher generalMonthDayMatcher = GENERAL_MONTH_DAY_PATTERN.matcher(expression);
			if (generalMonthDayMatcher.matches()) {
				String monthEx = generalMonthDayMatcher.group(1);
				String comment = getMonthEx(now, monthEx) + generalMonthDayMatcher.group(2);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher yearMonthLastDayMatcher = GENERAL_YEAR_MONTH_LAST_DAY_PATTERN.matcher(expression);
			if (yearMonthLastDayMatcher.matches()) {
				String yearEx = yearMonthLastDayMatcher.group(1);
				String monthEx = yearMonthLastDayMatcher.group(2);
				String comment = getGeneralYearMonthLastDayEx(now, yearEx, Integer.valueOf(monthEx));
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher monthLastDayMatcher = GENERAL_MONTH_LAST_DAY_PATTERN.matcher(expression);
			if (monthLastDayMatcher.matches()) {
				String monthEx = monthLastDayMatcher.group(1);
				String comment = getMonthLastDayEx(now, monthEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher weekDayMatcher = WEEK_DAY_PATTERN.matcher(expression);
			if (weekDayMatcher.matches()) {
				int weekDay = Integer.parseInt(weekDayMatcher.group(1));
				String comment = getWeekDayEx(now, weekDay);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher generalWeekDayMatcher = GENERAL_WEEK_SPECIFIC_DAY_PATTERN.matcher(expression);
			if (generalWeekDayMatcher.matches()) {
				String weekEx = generalWeekDayMatcher.group(1);
				int day = Integer.parseInt(generalWeekDayMatcher.group(2));
				String comment = getGeneralWeekDayEx(now, weekEx, day);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher specificYearQuarterMatcher = SPECIFIC_YEAR_QUARTER_PATTERN.matcher(expression);
			if (specificYearQuarterMatcher.matches()) {
				dateTimeCommentList.add(expression + "=" + expression);
				continue;
			}

			Matcher generalYearQuarterMatcher = GENERAL_YEAR_QUARTER_PATTERN.matcher(expression);
			if (generalYearQuarterMatcher.matches()) {
				String yearEx = generalYearQuarterMatcher.group(1);
				String quarterEx = generalYearQuarterMatcher.group(2);
				String comment = getYearEx(now, yearEx, false) + quarterEx;
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher generalQuarterMatcher = GENERAL_QUARTER_PATTERN.matcher(expression);
			if (generalQuarterMatcher.matches()) {
				String quarterEx = generalQuarterMatcher.group(1);
				String comment = getQuarterEx(now, quarterEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher generalWeekMatcher = GENERAL_WEEK_PATTERN.matcher(expression);
			if (generalWeekMatcher.matches()) {
				String weekEx = generalWeekMatcher.group(1);
				String comment = getWeekEx(now, weekEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher specificYearWeekMatcher = SPECIFIC_YEAR_WEEK_PATTERN.matcher(expression);
			if (specificYearWeekMatcher.matches()) {
				int yearEx = Integer.parseInt(specificYearWeekMatcher.group(1));
				int weekEx = Integer.parseInt(specificYearWeekMatcher.group(2));
				String comment = getSpecificYearWeekEx(now, yearEx, weekEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher generalYearWeekMatcher = GENERAL_YEAR_WEEK_PATTERN.matcher(expression);
			if (generalYearWeekMatcher.matches()) {
				String yearEx = generalYearWeekMatcher.group(1);
				int weekEx = Integer.parseInt(generalYearWeekMatcher.group(2));
				String comment = getGeneralYearWeekEx(now, yearEx, weekEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher generalMonthWeekMatcher = GENERAL_MONTH_WEEK_PATTERN.matcher(expression);
			if (generalMonthWeekMatcher.matches()) {
				String monthEx = generalMonthWeekMatcher.group(1);
				int weekEx = Integer.parseInt(generalMonthWeekMatcher.group(2));
				String comment = getGeneralMonthWeekEx(now, monthEx, weekEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher specificYearMonthLastWeekMatcher = SPECIFIC_YEAR_MONTH_LAST_WEEK_PATTERN.matcher(expression);
			if (specificYearMonthLastWeekMatcher.matches()) {
				int yearEx = Integer.parseInt(specificYearMonthLastWeekMatcher.group(1));
				int monthEx = Integer.parseInt(specificYearMonthLastWeekMatcher.group(2));
				String comment = getSpecificYearMonthLastWeek(now, yearEx, monthEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher generalMonthLastWeekMatcher = GENERAL_MONTH_LAST_WEEK_PATTERN.matcher(expression);
			if (generalMonthLastWeekMatcher.matches()) {
				String monthEx = generalMonthLastWeekMatcher.group(1);
				String comment = getGeneralMonthLastWeek(now, monthEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher generalMonthLastCompleteWeekMatcher = GENERAL_MONTH_LAST_COMPLETE_WEEK_PATTERN.matcher(expression);
			if (generalMonthLastCompleteWeekMatcher.matches()) {
				String monthEx = generalMonthLastCompleteWeekMatcher.group(1);
				String comment = getGeneralMonthLastCompleteWeekEx(now, monthEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher recentNYearMatcher = RECENT_N_YEAR_PATTERN.matcher(expression);
			if (recentNYearMatcher.matches()) {
				int n = Integer.parseInt(recentNYearMatcher.group(1));
				String comment = getRecentNYear(now, n);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher recentNMonthMatcher = RECENT_N_MONTH_PATTERN.matcher(expression);
			if (recentNMonthMatcher.matches()) {
				int n = Integer.parseInt(recentNMonthMatcher.group(1));
				String comment = getRecentNMonth(now, n);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher recentNWeekMatcher = RECENT_N_WEEK_PATTERN.matcher(expression);
			if (recentNWeekMatcher.matches()) {
				int n = Integer.parseInt(recentNWeekMatcher.group(1));
				String comment = getRecentNWeek(now, n);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher recentNDayWithoutTodayMatcher = RECENT_N_DAY_WITHOUT_TODAY_PATTERN.matcher(expression);
			if (recentNDayWithoutTodayMatcher.matches()) {
				int n = Integer.parseInt(recentNDayWithoutTodayMatcher.group(1));
				String comment = getRecentNDayWithoutToday(now, n);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher recentNDayMatcher = RECENT_N_DAY_PATTERN.matcher(expression);
			if (recentNDayMatcher.matches()) {
				int n = Integer.parseInt(recentNDayMatcher.group(1));
				String comment = getRecentNDay(now, n);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher recentNCompleteYearMatcher = RECENT_N_COMPLETE_YEAR_PATTERN.matcher(expression);
			if (recentNCompleteYearMatcher.matches()) {
				int n = Integer.parseInt(recentNCompleteYearMatcher.group(1));
				String comment = getRecentNCompleteYear(now, n);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher recentNCompleteQuarterMatcher = RECENT_N_COMPLETE_QUARTER_PATTERN.matcher(expression);
			if (recentNCompleteQuarterMatcher.matches()) {
				int n = Integer.parseInt(recentNCompleteQuarterMatcher.group(1));
				String comment = getRecentNCompleteQuarter(now, n);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher recentNCompleteMonthMatcher = RECENT_N_COMPLETE_MONTH_PATTERN.matcher(expression);
			if (recentNCompleteMonthMatcher.matches()) {
				int n = Integer.parseInt(recentNCompleteMonthMatcher.group(1));
				String comment = getRecentNCompleteMonth(now, n);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher recentNCompleteWeekMatcher = RECENT_N_COMPLETE_WEEK_PATTERN.matcher(expression);
			if (recentNCompleteWeekMatcher.matches()) {
				int n = Integer.parseInt(recentNCompleteWeekMatcher.group(1));
				String comment = getRecentNCompleteWeek(now, n);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher recentNQuarterWithCurrentMatcher = RECENT_N_QUARTER_WITH_CURRENT_PATTERN.matcher(expression);
			if (recentNQuarterWithCurrentMatcher.matches()) {
				int n = Integer.parseInt(recentNQuarterWithCurrentMatcher.group(1));
				String comment = getRecentNQuarterWithCurrent(now, n);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher specificYearMonthMatcher = SPECIFIC_YEAR_MONTH_PATTERN.matcher(expression);
			if (specificYearMonthMatcher.matches()) {
				dateTimeCommentList.add(expression + "=" + expression);
				continue;
			}

			Matcher generalYearMonthMatcher = GENERAL_YEAR_MONTH_PATTERN.matcher(expression);
			if (generalYearMonthMatcher.matches()) {
				String yearEx = generalYearMonthMatcher.group(1);
				String comment = getYearEx(now, yearEx, false) + generalYearMonthMatcher.group(2);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher generalDayMatcher = GENERAL_DAY_PATTERN.matcher(expression);
			if (generalDayMatcher.matches()) {
				String dayEx = generalDayMatcher.group(1);
				String comment = getDayEx(now, dayEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher generalMonthMatcher = GENERAL_MONTH_PATTERN.matcher(expression);
			if (generalMonthMatcher.matches()) {
				String monthEx = generalMonthMatcher.group(1);
				String comment = getMonthEx(now, monthEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher specificYearMatcher = SPECIFIC_YEAR_PATTERN.matcher(expression);
			if (specificYearMatcher.matches()) {
				int yearEx = Integer.parseInt(specificYearMatcher.group(1));
				String comment = String.valueOf(yearEx) + "年";
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher generalYearMatcher = GENERAL_YEAR_PATTERN.matcher(expression);
			if (generalYearMatcher.matches()) {
				String yearEx = generalYearMatcher.group(1);
				String comment = getYearEx(now, yearEx, true);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher specificYearMonthWeekMatcher = SPECIFIC_YEAR_MONTH_WEEK_PATTERN.matcher(expression);
			if (specificYearMonthWeekMatcher.matches()) {
				int yearEx = Integer.parseInt(specificYearMonthWeekMatcher.group(1));
				int monthEx = Integer.parseInt(specificYearMonthWeekMatcher.group(2));
				int weekEx = Integer.parseInt(specificYearMonthWeekMatcher.group(3));
				String comment = getSpecificYearMonthWeekEx(now, yearEx, monthEx, weekEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher generalYearMonthWeekMatcher = GENERAL_YEAR_MONTH_WEEK_PATTERN.matcher(expression);
			if (generalYearMonthWeekMatcher.matches()) {
				String yearEx = generalYearMonthWeekMatcher.group(1);
				int monthEx = Integer.parseInt(generalYearMonthWeekMatcher.group(2));
				int weekEx = Integer.parseInt(generalYearMonthWeekMatcher.group(3));
				String comment = getGeneralYearMonthWeekEx(now, yearEx, monthEx, weekEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher specificYearMonthCompleteWeekMatcher = SPECIFIC_YEAR_MONTH_COMPLETE_WEEK_PATTERN
				.matcher(expression);
			if (specificYearMonthCompleteWeekMatcher.matches()) {
				int yearEx = Integer.parseInt(specificYearMonthCompleteWeekMatcher.group(1));
				int monthEx = Integer.parseInt(specificYearMonthCompleteWeekMatcher.group(2));
				int weekEx = Integer.parseInt(specificYearMonthCompleteWeekMatcher.group(3));
				String comment = getSpecificYearMonthCompleteWeekEx(now, yearEx, monthEx, weekEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher generalYearMonthCompleteWeekMatcher = GENERAL_YEAR_MONTH_COMPLETE_WEEK_PATTERN.matcher(expression);
			if (generalYearMonthCompleteWeekMatcher.matches()) {
				String yearEx = generalYearMonthCompleteWeekMatcher.group(1);
				int monthEx = Integer.parseInt(generalYearMonthCompleteWeekMatcher.group(2));
				int weekEx = Integer.parseInt(generalYearMonthCompleteWeekMatcher.group(3));
				String comment = getGeneralYearMonthCompleteWeekEx(now, yearEx, monthEx, weekEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher generalMonthCompleteWeekMatcher = GENERAL_MONTH_COMPLETE_WEEK_PATTERN.matcher(expression);
			if (generalMonthCompleteWeekMatcher.matches()) {
				String monthEx = generalMonthCompleteWeekMatcher.group(1);
				int weekEx = Integer.parseInt(generalMonthCompleteWeekMatcher.group(2));
				String comment = getGeneralMonthCompleteWeekEx(now, monthEx, weekEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher specificYearCompleteWeekMatcher = SPECIFIC_YEAR_COMPLETE_WEEK_PATTERN.matcher(expression);
			if (specificYearCompleteWeekMatcher.matches()) {
				int yearEx = Integer.parseInt(specificYearCompleteWeekMatcher.group(1));
				int weekEx = Integer.parseInt(specificYearCompleteWeekMatcher.group(2));
				String comment = getSpecificYearCompleteWeekEx(now, yearEx, weekEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher generalYearCompleteWeekMatcher = GENERAL_YEAR_COMPLETE_WEEK_PATTERN.matcher(expression);
			if (generalYearCompleteWeekMatcher.matches()) {
				String yearEx = generalYearCompleteWeekMatcher.group(1);
				int weekEx = Integer.parseInt(generalYearCompleteWeekMatcher.group(2));
				String comment = getGeneralYearCompleteWeekEx(now, yearEx, weekEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher specificYearHalfYearMatcher = SPECIFIC_YEAR_HALF_YEAR_PATTERN.matcher(expression);
			if (specificYearHalfYearMatcher.matches()) {
				int yearEx = Integer.parseInt(specificYearHalfYearMatcher.group(1));
				String halfYearEx = specificYearHalfYearMatcher.group(2);
				String comment = getSpecificYearHalfYearEx(now, yearEx, halfYearEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher generalYearHalfYearMatcher = GENERAL_YEAR_HALF_YEAR_PATTERN.matcher(expression);
			if (generalYearHalfYearMatcher.matches()) {
				String yearEx = generalYearHalfYearMatcher.group(1);
				String halfYearEx = generalYearHalfYearMatcher.group(2);
				String comment = getGeneralYearHalfYearEx(now, yearEx, halfYearEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

			Matcher halfYearMatcher = HALF_YEAR_PATTERN.matcher(expression);
			if (halfYearMatcher.matches()) {
				String halfYearEx = halfYearMatcher.group(1);
				String comment = getSpecificYearHalfYearEx(now, now.getYear(), halfYearEx);
				dateTimeCommentList.add(expression + "=" + comment);
				continue;
			}

		}

		return dateTimeCommentList;
	}

	/**
	 * 将相对年份表达式（今年/去年/前年/明年/后年）解析为具体的年份描述。
	 * @param now 当前日期，作为相对计算的基准
	 * @param yearEx 相对年份表达式
	 * @param applyDomainLogic 是否应用领域特定逻辑（保留参数，用于扩展）
	 * @return 具体年份描述，如 "2024年"
	 */
	public static String getYearEx(LocalDate now, String yearEx, boolean applyDomainLogic) {
		String comment = "";
		int year = 0;
		// 依据相对表达式计算具体年份
		if (yearEx.equals("今年")) {
			year = now.getYear();
		}
		else if (yearEx.equals("去年")) {
			year = now.getYear() - 1;
		}
		else if (yearEx.equals("前年")) {
			year = now.getYear() - 2;
		}
		else if (yearEx.equals("明年")) {
			year = now.getYear() + 1;
		}
		else if (yearEx.equals("后年")) {
			year = now.getYear() + 2;
		}

		comment = String.valueOf(year) + "年";

		return comment;
	}

	/**
	 * 将相对月份表达式（本月/上月/上上月/下月/去年本月）解析为具体的年月描述。
	 * @param now 当前日期，作为相对计算的基准
	 * @param monthEx 相对月份表达式
	 * @return 具体年月描述，格式为 "yyyy年MM月"
	 */
	public static String getMonthEx(LocalDate now, String monthEx) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月");
		String comment = "";
		// 依据相对表达式在当前年月基础上进行偏移
		if (monthEx.equals("本月")) {
			comment = formatter.format(YearMonth.from(now));
		}
		else if (monthEx.equals("上月")) {
			comment = formatter.format(YearMonth.from(now).minusMonths(1));
		}
		else if (monthEx.equals("上上月")) {
			comment = formatter.format(YearMonth.from(now).minusMonths(2));
		}
		else if (monthEx.equals("下月")) {
			comment = formatter.format(YearMonth.from(now).plusMonths(1));
		}
		else if (monthEx.equals("去年本月")) {
			comment = formatter.format(YearMonth.from(now).minusYears(1));
		}
		return comment;
	}

	/**
	 * 将相对日期表达式（今天/昨天/前天/明天/后天/上月今天/上上月今天）解析为具体日期描述。
	 * @param now 当前日期，作为相对计算的基准
	 * @param dayEx 相对日期表达式
	 * @return 具体日期描述，格式为 "yyyy年MM月dd日"；解析异常时返回空字符串
	 */
	public static String getDayEx(LocalDate now, String dayEx) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		String comment = "";
		try {
			// 依据相对表达式计算具体日期
			if (dayEx.equals("今天")) {
				comment = formatter.format(now);
			}
			else if (dayEx.equals("昨天")) {
				comment = formatter.format(now.minusDays(1));
			}
			else if (dayEx.equals("前天")) {
				comment = formatter.format(now.minusDays(2));
			}
			else if (dayEx.equals("明天")) {
				comment = formatter.format(now.plusDays(1));
			}
			else if (dayEx.equals("后天")) {
				comment = formatter.format(now.plusDays(2));
			}
			else if (dayEx.equals("上月今天")) {
				comment = formatter.format(YearMonth.from(now).minusMonths(1).atDay(now.getDayOfMonth()));
			}
			else if (dayEx.equals("上上月今天")) {
				comment = formatter.format(YearMonth.from(now).minusMonths(2).atDay(now.getDayOfMonth()));
			}
		}
		catch (Exception e) {
			// 例如上月不存在今天（如 1月31日的上月今天）时，打印堆栈并返回空串
			e.printStackTrace();
		}
		return comment;
	}

	public static final String getWeekDayEx(LocalDate now, int x) {

		// Calculate date of first day of week (Monday)
		LocalDate monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		// Get date of xth day of week by adding (x - 1) days
		LocalDate desiredDay = monday.plusDays(x - 1);

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(desiredDay);
	}

	public static final String getGeneralWeekDayEx(LocalDate now, String weekEx, int day) {
		LocalDate thisMonday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate desiredDay = thisMonday.plusDays(day - 1);
		if (weekEx.equals("本周")) {

		}
		else if (weekEx.equals("上周")) {
			desiredDay = desiredDay.minusWeeks(1);
		}
		else if (weekEx.equals("上上周")) {
			desiredDay = desiredDay.minusWeeks(2);
		}
		else if (weekEx.equals("下周")) {
			desiredDay = desiredDay.plusWeeks(1);
		}
		else if (weekEx.equals("下下周")) {
			desiredDay = desiredDay.plusWeeks(2);
		}
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(desiredDay);
	}

	public static final String getWeekEx(LocalDate now, String weekEx) {
		LocalDate desireMonday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate desireSunday = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
		if (weekEx.equals("本周")) {

		}
		else if (weekEx.equals("上周")) {
			desireMonday = desireMonday.minusWeeks(1);
			desireSunday = desireSunday.minusWeeks(1);
		}
		else if (weekEx.equals("上上周")) {
			desireMonday = desireMonday.minusWeeks(2);
			desireSunday = desireSunday.minusWeeks(2);
		}
		else if (weekEx.equals("下周")) {
			desireMonday = desireMonday.plusWeeks(1);
			desireSunday = desireSunday.plusWeeks(1);
		}
		else if (weekEx.equals("下下周")) {
			desireMonday = desireMonday.plusWeeks(2);
			desireSunday = desireSunday.plusWeeks(2);
		}
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(desireMonday) + "至" + formatter.format(desireSunday);
	}

	public static String getSpecificYearWeekEx(LocalDate now, int year, int week) {
		LocalDate firstDayOfYear = LocalDate.of(year, 1, 1);
		LocalDate targetWeekFirstDay = firstDayOfYear.plusWeeks(week - 1);
		LocalDate targetWeekLastDay = targetWeekFirstDay.plusDays(6);
		LocalDate lastDayOfYear = firstDayOfYear.with(TemporalAdjusters.lastDayOfYear());
		if (lastDayOfYear.isBefore(targetWeekLastDay)) {
			targetWeekLastDay = lastDayOfYear;
		}
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(targetWeekFirstDay) + "至" + formatter.format(targetWeekLastDay);
	}

	public static String getGeneralYearWeekEx(LocalDate now, String yearEx, int week) {
		int year = now.getYear();
		if (yearEx.equals("今年")) {

		}
		else if (yearEx.equals("去年")) {
			year = now.getYear() - 1;
		}
		else if (yearEx.equals("前年")) {
			year = now.getYear() - 2;
		}
		else if (yearEx.equals("明年")) {
			year = now.getYear() + 1;
		}
		else if (yearEx.equals("后年")) {
			year = now.getYear() + 2;
		}
		return getSpecificYearWeekEx(now, year, week);
	}

	public static String getSpecificYearMonthWeekEx(LocalDate now, int year, int month, int week) {
		LocalDate firstDayOfMonth = LocalDate.of(year, month, 1);
		LocalDate targetWeekFirstDay = firstDayOfMonth.plusWeeks(week - 1);
		LocalDate targetWeekLastDay = targetWeekFirstDay.plusDays(6);
		LocalDate lastDayOfMonth = firstDayOfMonth.with(TemporalAdjusters.lastDayOfMonth());
		if (lastDayOfMonth.isBefore(targetWeekLastDay)) {
			targetWeekLastDay = lastDayOfMonth;
		}
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(targetWeekFirstDay) + "至" + formatter.format(targetWeekLastDay);
	}

	public static String getGeneralYearMonthWeekEx(LocalDate now, String yearEx, int month, int week) {
		int year = now.getYear();
		if (yearEx.equals("今年")) {

		}
		else if (yearEx.equals("去年")) {
			year = now.getYear() - 1;
		}
		else if (yearEx.equals("前年")) {
			year = now.getYear() - 2;
		}
		else if (yearEx.equals("明年")) {
			year = now.getYear() + 1;
		}
		else if (yearEx.equals("后年")) {
			year = now.getYear() + 2;
		}
		return getSpecificYearMonthWeekEx(now, year, month, week);
	}

	public static String getGeneralMonthWeekEx(LocalDate now, String monthEx, int week) {
		int year = now.getYear();
		int month = now.getMonthValue();
		if (monthEx.equals("本月")) {

		}
		else if (monthEx.equals("上月")) {
			month = now.getMonthValue() - 1;
			if (month <= 0) {
				year--;
				month = 12 + month;
			}
		}
		LocalDate firstDayOfMonth = LocalDate.of(year, month, 1);
		LocalDate targetWeekFirstDay = firstDayOfMonth.plusWeeks(week - 1);
		LocalDate targetWeekLastDay = targetWeekFirstDay.plusDays(6);
		LocalDate lastDayOfMonth = firstDayOfMonth.with(TemporalAdjusters.lastDayOfMonth());
		if (lastDayOfMonth.isBefore(targetWeekLastDay)) {
			targetWeekLastDay = lastDayOfMonth;
		}
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(targetWeekFirstDay) + "至" + formatter.format(targetWeekLastDay);
	}

	public static String getSpecificYearMonthCompleteWeekEx(LocalDate now, int year, int month, int week) {
		LocalDate firstDayOfMonth = LocalDate.of(year, month, 1);
		LocalDate firstMonday = firstDayOfMonth.with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
		LocalDate targetStartDate = firstMonday.plusWeeks(week - 1);
		LocalDate targetEndDate = targetStartDate.plusDays(6);
		LocalDate lastDayOfMonth = firstDayOfMonth.with(TemporalAdjusters.lastDayOfMonth());
		if (lastDayOfMonth.isBefore(targetEndDate)) {
			return StringUtils.EMPTY;
		}
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(targetStartDate) + "至" + formatter.format(targetEndDate);
	}

	public static String getGeneralYearMonthCompleteWeekEx(LocalDate now, String yearEx, int month, int week) {
		int year = now.getYear();
		if (yearEx.equals("今年")) {

		}
		else if (yearEx.equals("去年")) {
			year = now.getYear() - 1;
		}
		else if (yearEx.equals("前年")) {
			year = now.getYear() - 2;
		}
		else if (yearEx.equals("明年")) {
			year = now.getYear() + 1;
		}
		else if (yearEx.equals("后年")) {
			year = now.getYear() + 2;
		}
		return getSpecificYearMonthCompleteWeekEx(now, year, month, week);
	}

	public static String getGeneralMonthCompleteWeekEx(LocalDate now, String monthEx, int week) {
		int year = now.getYear();
		int month = now.getMonthValue();
		if (monthEx.equals("本月")) {

		}
		else if (monthEx.equals("上月")) {
			month = now.getMonthValue() - 1;
			if (month <= 0) {
				year--;
				month = 12 + month;
			}
		}
		else if (monthEx.equals("上上月")) {
			month = now.getMonthValue() - 2;
			if (month <= 0) {
				year--;
				month = 12 + month;
			}
		}
		else if (monthEx.equals("下月")) {
			month = now.getMonthValue() + 1;
			if (month > 12) {
				year++;
				month = month - 12;
			}
		}
		return getSpecificYearMonthCompleteWeekEx(now, year, month, week);
	}

	public static String getSpecificYearCompleteWeekEx(LocalDate now, int year, int week) {
		LocalDate firstDayOfYear = LocalDate.of(year, 1, 1);
		LocalDate firstMonday = firstDayOfYear.with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
		LocalDate targetStartDate = firstMonday.plusWeeks(week - 1);
		LocalDate targetEndDate = targetStartDate.plusDays(6);
		LocalDate lastDayOfYear = firstDayOfYear.with(TemporalAdjusters.lastDayOfYear());
		if (lastDayOfYear.isBefore(targetEndDate)) {
			return StringUtils.EMPTY;
		}
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(targetStartDate) + "至" + formatter.format(targetEndDate);
	}

	public static String getGeneralYearCompleteWeekEx(LocalDate now, String yearEx, int week) {
		int year = now.getYear();
		if (yearEx.equals("今年")) {

		}
		else if (yearEx.equals("去年")) {
			year = now.getYear() - 1;
		}
		else if (yearEx.equals("前年")) {
			year = now.getYear() - 2;
		}
		else if (yearEx.equals("明年")) {
			year = now.getYear() + 1;
		}
		else if (yearEx.equals("后年")) {
			year = now.getYear() + 2;
		}
		return getSpecificYearCompleteWeekEx(now, year, week);
	}

	public static String getSpecificYearMonthLastWeek(LocalDate now, int year, int month) {
		LocalDate firstDayOfMonth = LocalDate.of(year, month, 1);
		LocalDate lastDayOfMonth = firstDayOfMonth.with(TemporalAdjusters.lastDayOfMonth());
		LocalDate previousMonday = lastDayOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(previousMonday) + "至" + formatter.format(lastDayOfMonth);
	}

	public static String getGeneralMonthLastWeek(LocalDate now, String monthEx) {
		int year = now.getYear();
		int month = now.getMonthValue();
		if (monthEx.equals("本月")) {

		}
		else if (monthEx.equals("上月")) {
			month = now.getMonthValue() - 1;
			if (month <= 0) {
				year--;
				month = 12 + month;
			}
		}
		else if (monthEx.equals("上上月")) {
			month = now.getMonthValue() - 2;
			if (month <= 0) {
				year--;
				month = 12 + month;
			}
		}
		else if (monthEx.equals("下月")) {
			month = now.getMonthValue() + 1;
			if (month > 12) {
				year++;
				month = month - 12;
			}
		}
		return getSpecificYearMonthLastWeek(now, year, month);
	}

	public static String getSpecificYearMonthLastCompleteWeekEx(LocalDate now, int year, int month) {
		LocalDate firstDayOfMonth = LocalDate.of(year, month, 1);
		LocalDate lastSunday = firstDayOfMonth.with(TemporalAdjusters.lastInMonth(DayOfWeek.SUNDAY));
		LocalDate lastMonday = lastSunday.minusDays(6);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(lastMonday) + "至" + formatter.format(lastSunday);
	}

	public static String getGeneralMonthLastCompleteWeekEx(LocalDate now, String monthEx) {
		int year = now.getYear();
		int month = now.getMonthValue();
		if (monthEx.equals("本月")) {

		}
		else if (monthEx.equals("上月")) {
			month = now.getMonthValue() - 1;
			if (month <= 0) {
				year--;
				month = 12 + month;
			}
		}
		else if (monthEx.equals("上上月")) {
			month = now.getMonthValue() - 2;
			if (month <= 0) {
				year--;
				month = 12 + month;
			}
		}
		else if (monthEx.equals("下月")) {
			month = now.getMonthValue() + 1;
			if (month > 12) {
				year++;
				month = month - 12;
			}
		}
		return getSpecificYearMonthLastCompleteWeekEx(now, year, month);
	}

	public static String getQuarterEx(LocalDate now, String quarterEx) {
		int currentQuarter = now.get(IsoFields.QUARTER_OF_YEAR);
		// Calculate previous and next quarters
		int lastQuarter = currentQuarter == 1 ? 4 : currentQuarter - 1;
		int nextQuarter = currentQuarter == 4 ? 1 : currentQuarter + 1;
		int currentYear = now.getYear();
		int yearOfLastQuarter = (currentQuarter == 1) ? currentYear - 1 : currentYear;
		int yearOfNextQuarter = (currentQuarter == 4) ? currentYear + 1 : currentYear;
		int yearOfSameQuarterLastYear = currentYear - 1;

		String comment = "";
		if (quarterEx.equals("本季度")) {
			comment = currentYear + "年第" + currentQuarter + "季度";
		}
		else if (quarterEx.equals("上季度")) {
			comment = yearOfLastQuarter + "年第" + lastQuarter + "季度";
		}
		else if (quarterEx.equals("下季度")) {
			comment = yearOfNextQuarter + "年第" + nextQuarter + "季度";
		}
		else if (quarterEx.equals("去年本季度")) {
			comment = yearOfSameQuarterLastYear + "年第" + currentQuarter + "季度";
		}
		return comment;
	}

	public static String getRecentNYear(LocalDate now, int n) {
		LocalDate startDate = now.minusYears(n);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(startDate) + "至" + formatter.format(now);
	}

	public static String getRecentNMonth(LocalDate now, int n) {
		LocalDate startDate = now.minusMonths(n);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(startDate) + "至" + formatter.format(now);
	}

	public static String getRecentNWeek(LocalDate now, int n) {
		LocalDate startDate = now.minusWeeks(n);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(startDate) + "至" + formatter.format(now);
	}

	public static String getRecentNDay(LocalDate now, int n) {
		LocalDate startDate = now.minusDays(n);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(startDate) + "至" + formatter.format(now);
	}

	public static String getRecentNCompleteYear(LocalDate now, int n) {
		LocalDate endDate;
		if (now.getMonthValue() == 12 && now.getDayOfMonth() == 31) {
			endDate = now;
		}
		else {
			endDate = now.with(TemporalAdjusters.lastDayOfYear()).minusYears(1);
		}
		LocalDate startDate = endDate.minusYears(n).plusDays(1);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(startDate) + "至" + formatter.format(endDate);
	}

	public static String getRecentNCompleteMonth(LocalDate now, int n) {
		LocalDate endDate;
		if (now.equals(now.with(TemporalAdjusters.lastDayOfMonth()))) {
			endDate = now;
		}
		else {
			endDate = now.with(TemporalAdjusters.firstDayOfMonth())
				.minusMonths(1)
				.with(TemporalAdjusters.lastDayOfMonth());
		}
		LocalDate startDate = endDate.minusMonths(n).plusDays(1);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(startDate) + "至" + formatter.format(endDate);
	}

	public static String getRecentNCompleteQuarter(LocalDate now, int n) {
		LocalDate endDate;
		int currentMonth = now.getMonthValue();
		if (currentMonth % 4 == 0 && now.getDayOfMonth() == 31) {
			endDate = now;
		}
		else {
			if (currentMonth < 4) {
				endDate = LocalDate.of(now.getYear() - 1, 12, 31);
			}
			else if (currentMonth < 7) {
				endDate = LocalDate.of(now.getYear(), 3, 31);
			}
			else if (currentMonth < 10) {
				endDate = LocalDate.of(now.getYear(), 6, 30);
			}
			else {
				endDate = LocalDate.of(now.getYear(), 9, 30);
			}
		}
		LocalDate startDate = endDate.minusMonths(n * 3).plusDays(1);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(startDate) + "至" + formatter.format(endDate);
	}

	public static String getRecentNCompleteWeek(LocalDate now, int n) {
		LocalDate endDate;
		if (now.getDayOfWeek().getValue() == 7) {
			endDate = now;
		}
		else {
			endDate = now.minusWeeks(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
		}
		LocalDate startDate = endDate.minusWeeks(n).plusDays(1);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(startDate) + "至" + formatter.format(endDate);
	}

	public static String getRecentNDayWithoutToday(LocalDate now, int n) {
		LocalDate startDate = now.minusDays(n);
		LocalDate endDate = now.minusDays(1);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(startDate) + "至" + formatter.format(endDate);
	}

	public static String getRecentNQuarterWithCurrent(LocalDate now, int n) {
		LocalDate endDate;
		int currentMonth = now.getMonthValue();
		if (currentMonth < 4) {
			endDate = LocalDate.of(now.getYear(), 3, 31);
		}
		else if (currentMonth < 7) {
			endDate = LocalDate.of(now.getYear(), 6, 30);
		}
		else if (currentMonth < 10) {
			endDate = LocalDate.of(now.getYear(), 9, 30);
		}
		else {
			endDate = LocalDate.of(now.getYear(), 12, 31);
		}
		LocalDate startDate = endDate.minusMonths(n * 3).plusDays(1);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(startDate) + "至" + formatter.format(endDate);
	}

	public static String getMonthLastDayEx(LocalDate now, String monthEx) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		String comment = "";
		if (monthEx.equals("本月")) {
			comment = formatter.format(YearMonth.from(now).atEndOfMonth());
		}
		else if (monthEx.equals("上月")) {
			comment = formatter.format(YearMonth.from(now).minusMonths(1).atEndOfMonth());
		}
		return comment;
	}

	public static String getGeneralYearMonthLastDayEx(LocalDate now, String yearEx, int month) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		String comment = "";
		int year = 0;
		if (yearEx.equals("今年")) {
			year = now.getYear();
		}
		else if (yearEx.equals("去年")) {
			year = now.getYear() - 1;
		}
		else if (yearEx.equals("前年")) {
			year = now.getYear() - 2;
		}
		else if (yearEx.equals("明年")) {
			year = now.getYear() + 1;
		}
		else if (yearEx.equals("后年")) {
			year = now.getYear() + 2;
		}
		comment = formatter.format(YearMonth.of(year, month).atEndOfMonth());
		return comment;
	}

	public static String getSpecificYearHalfYearEx(LocalDate now, int year, String halfYearEx) {
		LocalDate startDate;
		LocalDate endDate;
		if (halfYearEx.equals("上")) {
			startDate = LocalDate.of(year, 1, 1);
			endDate = LocalDate.of(year, 6, 30);
		}
		else {
			startDate = LocalDate.of(year, 7, 1);
			endDate = LocalDate.of(year, 12, 31);
		}
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
		return formatter.format(startDate) + "至" + formatter.format(endDate);
	}

	public static String getGeneralYearHalfYearEx(LocalDate now, String yearEx, String halfYearEx) {
		int year = 0;
		if (yearEx.equals("今年")) {
			year = now.getYear();
		}
		else if (yearEx.equals("去年")) {
			year = now.getYear() - 1;
		}
		else if (yearEx.equals("前年")) {
			year = now.getYear() - 2;
		}
		else if (yearEx.equals("明年")) {
			year = now.getYear() + 1;
		}
		else if (yearEx.equals("后年")) {
			year = now.getYear() + 2;
		}
		return getSpecificYearHalfYearEx(now, year, halfYearEx);
	}

}
