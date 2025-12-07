package erp.approvalrequest.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import erp.approvalrequest.ApprovalRequestIntegrationTestSupport;

class RequestIdGeneratorTest extends ApprovalRequestIntegrationTestSupport {

    @Nested
    class NextId {

        @Test
        void 초기_시퀀스가_없으면_1을_반환한다() {
            // when
            long id = requestIdGenerator.nextId();

            // then: 반환값 검증
            assertThat(id).isEqualTo(1L);
        }

        @Test
        void 연속_호출하면_id가_1씩_증가한다() {
            // when
            long first = requestIdGenerator.nextId();
            long second = requestIdGenerator.nextId();
            long third = requestIdGenerator.nextId();

            // then: 반환값 검증
            assertThat(first).isEqualTo(1L);
            assertThat(second).isEqualTo(2L);
            assertThat(third).isEqualTo(3L);
        }

        @Test
        void 기존_시퀀스가_있어도_연속증가를_보장한다() {
            // given
            requestIdGenerator.nextId(); // 1 생성
            RequestIdGenerator another = new RequestIdGenerator(mongoTemplate);

            // when
            long next = another.nextId();

            // then: 반환값 검증
            assertThat(next).isEqualTo(2L);
        }
    }
}
