package com.apiece.springboot_sns_sample.domain.recommend;

import java.util.List;

public record RankResponse(
        Long userId,
        String segment, // user 가 속한 테스트 그룹 (segment 에 따라 지연응답, 정상응답 테스트 분기)
        List<Long> rankedPostIds,
        Long tookMs // 추천 서비스 내부에서 추천 처리에 걸린 시간
) {
}
