package cn.edu.academic.support.notification.dto;
public record TodoResponse(String type, String title, String description, Long targetId, String route, long count) {}
