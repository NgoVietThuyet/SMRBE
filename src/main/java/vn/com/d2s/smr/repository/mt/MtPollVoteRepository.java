package vn.com.d2s.smr.repository.mt;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.mt.MtPollVote;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MtPollVoteRepository extends JpaRepository<MtPollVote, String> {

    List<MtPollVote> findByPollIdIn(Collection<String> pollIds);

    Optional<MtPollVote> findByPollIdAndUserName(String pollId, String userName);

    void deleteByPollIdIn(Collection<String> pollIds);
}
