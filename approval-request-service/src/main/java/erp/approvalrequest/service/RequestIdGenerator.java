package erp.approvalrequest.service;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Component
public class RequestIdGenerator {

    private static final String SEQUENCE_ID = "approval_request_id";

    private final MongoOperations mongoOperations;

    public RequestIdGenerator(MongoOperations mongoOperations) {
        this.mongoOperations = mongoOperations;
    }

    public long nextId() {
        Query query = Query.query(Criteria.where("_id").is(SEQUENCE_ID));
        Update update = new Update().inc("value", 1);
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true).upsert(true);
        Sequence sequence = mongoOperations.findAndModify(query, update, options, Sequence.class);
        return sequence != null ? sequence.getValue() : 1L;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Sequence {
        @Id
        private String id;
        private long value;
    }
}
