package cn.wenchang.brain.workflow;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 使用真实坐标完成研学地点筛选和基本空间排序。
 *
 * <p>该服务只判断相对空间距离，不虚构精确驾车时间。</p>
 */
@Service
public class StudyTourPlanningService {

    public StudyTourPlan plan(StudyTourRequest request, List<StudyTourPlace> candidates) {
        if (request == null || request.ageGroup() == null || request.ageGroup().isBlank()) {
            throw new IllegalArgumentException("ageGroup is required");
        }
        if (candidates == null || candidates.isEmpty()) {
            return new StudyTourPlan(request.ageGroup(), List.of(),
                    List.of("没有找到同时满足主题、年龄与坐标要求的可核验地点。"));
        }

        Set<String> wantedThemes = normalize(request.themes());
        List<StudyTourPlace> eligible = candidates.stream()
                .filter(StudyTourPlace::hasCoordinates)
                .filter(place -> wantedThemes.isEmpty() || intersects(wantedThemes, normalize(place.themes())))
                .filter(place -> place.ageGroups() == null || place.ageGroups().isEmpty()
                        || normalize(place.ageGroups()).contains(request.ageGroup().trim().toLowerCase()))
                .toList();

        int limit = Math.max(1, Math.min(request.maxPlaces() <= 0 ? 3 : request.maxPlaces(), 5));
        List<StudyTourPlace> ordered = nearestNeighbour(eligible).stream().limit(limit).toList();
        List<StudyTourStop> stops = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            StudyTourPlace place = ordered.get(index);
            String slot = timeSlot(index, ordered.size());
            stops.add(new StudyTourStop(slot, place.name(), place.town(),
                    matchedTheme(place, wantedThemes), place.learningPoints(), index + 1,
                    place.sourceOrganization(), place.sourceUrl()));
        }
        List<String> notes = List.of(
                "地点顺序按公开坐标做直线空间聚类，不代表精确道路距离或驾车时间。",
                "出发前请通过地点官方渠道核验开放时间、预约和天气影响。"
        );
        return new StudyTourPlan(request.ageGroup().trim(), stops, notes);
    }

    private List<StudyTourPlace> nearestNeighbour(List<StudyTourPlace> input) {
        if (input.size() < 2) return input;
        List<StudyTourPlace> remaining = new ArrayList<>(input);
        remaining.sort(Comparator.comparingDouble(StudyTourPlace::longitude)
                .thenComparingDouble(StudyTourPlace::latitude));
        List<StudyTourPlace> result = new ArrayList<>();
        result.add(remaining.remove(0));
        while (!remaining.isEmpty()) {
            StudyTourPlace current = result.get(result.size() - 1);
            StudyTourPlace nearest = remaining.stream()
                    .min(Comparator.comparingDouble(candidate -> squaredDistance(current, candidate)))
                    .orElseThrow();
            result.add(nearest);
            remaining.remove(nearest);
        }
        return result;
    }

    private double squaredDistance(StudyTourPlace first, StudyTourPlace second) {
        double latitude = first.latitude() - second.latitude();
        double longitude = (first.longitude() - second.longitude())
                * Math.cos(Math.toRadians((first.latitude() + second.latitude()) / 2));
        return latitude * latitude + longitude * longitude;
    }

    private Set<String> normalize(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) values.stream().filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase()).forEach(result::add);
        return result;
    }

    private boolean intersects(Set<String> left, Set<String> right) {
        return left.stream().anyMatch(right::contains);
    }

    private String matchedTheme(StudyTourPlace place, Set<String> wanted) {
        return normalize(place.themes()).stream().filter(wanted::contains).findFirst()
                .orElseGet(() -> place.themes() == null || place.themes().isEmpty() ? "城市认知" : place.themes().get(0));
    }

    private String timeSlot(int index, int total) {
        if (total == 1) return "半日核心学习";
        return switch (index) {
            case 0 -> "上午";
            case 1 -> total == 2 ? "下午" : "中午至下午";
            default -> "下午";
        };
    }

    public record StudyTourRequest(String ageGroup, List<String> themes, int maxPlaces) { }

    public record StudyTourPlace(String id, String name, String town, double latitude, double longitude,
                                 List<String> themes, List<String> ageGroups, List<String> learningPoints,
                                 String sourceOrganization, String sourceUrl) {
        public boolean hasCoordinates() {
            return Double.isFinite(latitude) && Double.isFinite(longitude)
                    && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180
                    && !(latitude == 0 && longitude == 0);
        }
    }

    public record StudyTourStop(String timeSlot, String place, String town, String learningTheme,
                                List<String> learningContent, int order, String sourceOrganization,
                                String sourceUrl) { }

    public record StudyTourPlan(String ageGroup, List<StudyTourStop> stops, List<String> notes) {
        public StudyTourPlan {
            stops = List.copyOf(stops);
            notes = List.copyOf(notes);
        }
    }
}
