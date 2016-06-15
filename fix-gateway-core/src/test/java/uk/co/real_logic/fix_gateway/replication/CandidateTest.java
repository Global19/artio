/*
 * Copyright 2015-2016 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.fix_gateway.replication;

import io.aeron.Subscription;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;
import static uk.co.real_logic.fix_gateway.messages.Vote.AGAINST;
import static uk.co.real_logic.fix_gateway.messages.Vote.FOR;
import static uk.co.real_logic.fix_gateway.replication.ReplicationAsserts.neverTransitionsToFollower;
import static uk.co.real_logic.fix_gateway.replication.ReplicationAsserts.neverTransitionsToLeader;

public class CandidateTest
{
    private static final long POSITION = 40;
    private static final long VOTE_TIMEOUT = 100;
    private static final int OLD_LEADERSHIP_TERM = 1;
    private static final int NEW_LEADERSHIP_TERM = OLD_LEADERSHIP_TERM + 1;
    private static final int DATA_SESSION_ID = 42;
    private static final int CLUSTER_SIZE = 5;

    private static final short ID = 3;
    private static final short ID_4 = 4;
    private static final short ID_5 = 5;

    private RaftPublication controlPublication = mock(RaftPublication.class);
    private Subscription controlSubscription = mock(Subscription.class);
    private ClusterAgent clusterNode = mock(ClusterAgent.class);
    private TermState termState = new TermState();

    private Candidate candidate = new Candidate(
        ID, DATA_SESSION_ID, clusterNode, CLUSTER_SIZE, VOTE_TIMEOUT, termState, new QuorumAcknowledgementStrategy());

    @Before
    public void setUp()
    {
        candidate
            .controlPublication(controlPublication)
            .controlSubscription(controlSubscription);
    }

    @Test
    public void shouldNotCountVotesForWrongTerm()
    {
        startElection();

        candidate.onReplyVote(ID_4, ID, OLD_LEADERSHIP_TERM, FOR);
        candidate.onReplyVote(ID_5, ID, OLD_LEADERSHIP_TERM, FOR);

        neverTransitionsToLeader(clusterNode);
    }

    @Test
    public void shouldNotCountVotesAgainst()
    {
        startElection();

        candidate.onReplyVote(ID_4, ID, NEW_LEADERSHIP_TERM, AGAINST);
        candidate.onReplyVote(ID_5, ID, NEW_LEADERSHIP_TERM, AGAINST);

        neverTransitionsToLeader(clusterNode);
    }

    @Test
    public void shouldNotCountVotesForOtherCandidates()
    {
        final short otherCandidate = (short) 2;

        startElection();

        candidate.onReplyVote(ID_4, otherCandidate, NEW_LEADERSHIP_TERM, FOR);
        candidate.onReplyVote(ID_5, otherCandidate, NEW_LEADERSHIP_TERM, FOR);

        neverTransitionsToLeader(clusterNode);
    }

    @Test
    public void shouldNotDoubleCountVotes()
    {
        startElection();

        candidate.onReplyVote(ID_4, ID, NEW_LEADERSHIP_TERM, FOR);
        candidate.onReplyVote(ID_4, ID, NEW_LEADERSHIP_TERM, FOR);

        neverTransitionsToLeader(clusterNode);
    }

    @Test
    public void shouldRestartElectionIfTimeoutElapses()
    {
        startElection();

        candidate.poll(1, VOTE_TIMEOUT * 2 + 1);

        requestsVote(NEW_LEADERSHIP_TERM);
        requestsVote(NEW_LEADERSHIP_TERM + 1);

        neverTransitionsToLeader(clusterNode);
        neverTransitionsToFollower(clusterNode);
    }

    private void requestsVote(final int term)
    {
        verify(controlPublication, times(1)).saveRequestVote(ID, DATA_SESSION_ID, POSITION, term);
    }

    private void startElection()
    {
        termState.leadershipTerm(OLD_LEADERSHIP_TERM).consensusPosition(POSITION);
        candidate.startNewElection(0L);
    }
}
