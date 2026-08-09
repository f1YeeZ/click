package com.clicker.mousehub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clicker.mousehub.entity.PageViewEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PageViewEventMapper extends BaseMapper<PageViewEvent> {
    @Select("""
            SELECT view_date AS date,
                   COUNT(*) AS page_views,
                   COUNT(DISTINCT visitor_hash) AS unique_visitors
            FROM page_view_events
            WHERE view_date BETWEEN #{from} AND #{to}
            GROUP BY view_date
            ORDER BY view_date
            """)
    List<TrafficDayRow> aggregate(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("""
            SELECT COUNT(*) AS page_views,
                   COUNT(DISTINCT visitor_hash) AS unique_visitors
            FROM page_view_events
            WHERE view_date BETWEEN #{from} AND #{to}
            """)
    TrafficTotalsRow totals(@Param("from") LocalDate from, @Param("to") LocalDate to);

    class TrafficDayRow {
        private LocalDate date;
        private long pageViews;
        private long uniqueVisitors;

        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public long getPageViews() { return pageViews; }
        public void setPageViews(long pageViews) { this.pageViews = pageViews; }
        public long getUniqueVisitors() { return uniqueVisitors; }
        public void setUniqueVisitors(long uniqueVisitors) { this.uniqueVisitors = uniqueVisitors; }
    }

    class TrafficTotalsRow {
        private long pageViews;
        private long uniqueVisitors;

        public long getPageViews() { return pageViews; }
        public void setPageViews(long pageViews) { this.pageViews = pageViews; }
        public long getUniqueVisitors() { return uniqueVisitors; }
        public void setUniqueVisitors(long uniqueVisitors) { this.uniqueVisitors = uniqueVisitors; }
    }
}
