/**
 * Copyright 2013-2015 Pierre Merienne
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.pmerienne.trident.cf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import storm.trident.Stream;
import storm.trident.TridentTopology;
import storm.trident.operation.BaseFunction;
import storm.trident.operation.TridentCollector;
import storm.trident.testing.FixedBatchSpout;
import storm.trident.tuple.TridentTuple;
import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.LocalDRPC;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Values;

import com.github.pmerienne.trident.cf.state.InMemoryCFState;
import com.github.pmerienne.trident.cf.testing.DRPCUtils;

public class CFTopologyTest {

	@Test
	public void testPearsonSimilarityInTopology() throws InterruptedException {
		// Start local cluster
		LocalCluster cluster = new LocalCluster();
		LocalDRPC localDRPC = new LocalDRPC();

		try {
			// Build topology
			TridentTopology topology = new TridentTopology();

			// Create rating spout
			Values[] ratings = new Values[10];
			ratings[0] = new Values(0L, 0L, 0.0);
			ratings[1] = new Values(0L, 1L, 0.5);
			ratings[2] = new Values(0L, 2L, 0.9);
			ratings[3] = new Values(0L, 3L, 0.6);

			ratings[4] = new Values(1L, 0L, 0.1);
			ratings[5] = new Values(1L, 1L, 0.4);
			ratings[6] = new Values(1L, 3L, 0.7);

			ratings[7] = new Values(2L, 0L, 0.8);
			ratings[8] = new Values(2L, 2L, 0.2);
			ratings[9] = new Values(2L, 3L, 0.1);
			FixedBatchSpout ratingsSpout = new FixedBatchSpout(new Fields(CFTopology.DEFAULT_USER1_FIELD, CFTopology.DEFAULT_ITEM_FIELD, CFTopology.DEFAULT_RATING_FIELD), 5, ratings);

			// Create ratings stream
			Stream ratingStream = topology.newStream("ratings", ratingsSpout);

			// Create similarity query stream
			Stream similarityQueryStream = topology.newDRPCStream("userSimilarity", localDRPC).each(new Fields("args"), new ExtractUsers(),
					new Fields(CFTopology.DEFAULT_USER1_FIELD, CFTopology.DEFAULT_USER2_FIELD));

			// Create collaborative filtering topology with an in memory CF
			// state
			CFTopology cfTopology = new CFTopology(ratingStream, new InMemoryCFState.Factory());
			cfTopology.createUserSimilarityStream(similarityQueryStream);

			// Submit and wait topology
			cluster.submitTopology(this.getClass().getSimpleName(), new Config(), topology.build());
			Thread.sleep(5000);

			// Check expected similarity
			double expectedSimilarity01 = 0.8320502943378436;
			double actualSimilarity01 = (Double) DRPCUtils.extractSingleValue(localDRPC.execute("userSimilarity", "0 1"));
			assertEquals(expectedSimilarity01, actualSimilarity01, 10e-6);

			double expectedSimilarity12 = -0.9728062146853667;
			double actualSimilarity12 = (Double) DRPCUtils.extractSingleValue(localDRPC.execute("userSimilarity", "1 2"));
			assertEquals(expectedSimilarity12, actualSimilarity12, 10e-6);

			double expectedSimilarity02 = -0.8934051474415642;
			double actualSimilarity02 = (Double) DRPCUtils.extractSingleValue(localDRPC.execute("userSimilarity", "0 2"));
			assertEquals(expectedSimilarity02, actualSimilarity02, 10e-6);
		} finally {
			cluster.shutdown();
			localDRPC.shutdown();
		}
	}

	@Test
	public void testItemsRecommendationInTopology() throws InterruptedException {
		// Start local cluster
		LocalCluster cluster = new LocalCluster();
		LocalDRPC localDRPC = new LocalDRPC();

		try {
			// Build topology
			TridentTopology topology = new TridentTopology();

			// Create rating spout
			Values[] ratings = new Values[19];
			ratings[0] = new Values(0L, 0L, 0.9);
			ratings[1] = new Values(0L, 2L, 0.8);
			ratings[2] = new Values(0L, 3L, 0.1);
			ratings[3] = new Values(0L, 7L, 0.2);
			ratings[4] = new Values(0L, 8L, 0.9);

			ratings[5] = new Values(1L, 1L, 0.8);
			ratings[6] = new Values(1L, 3L, 0.9);
			ratings[7] = new Values(1L, 4L, 0.2);
			ratings[8] = new Values(1L, 5L, 0.8);
			ratings[9] = new Values(1L, 6L, 0.1);
			ratings[10] = new Values(1L, 7L, 0.8);
			ratings[11] = new Values(1L, 8L, 0.2);

			ratings[12] = new Values(2L, 0L, 0.8);
			ratings[13] = new Values(2L, 1L, 0.1);
			ratings[14] = new Values(2L, 2L, 0.9);
			ratings[15] = new Values(2L, 3L, 0.2);
			ratings[16] = new Values(2L, 4L, 0.8);
			ratings[17] = new Values(2L, 6L, 0.9);
			ratings[18] = new Values(2L, 8L, 0.9);
			FixedBatchSpout ratingsSpout = new FixedBatchSpout(new Fields(CFTopology.DEFAULT_USER1_FIELD, CFTopology.DEFAULT_ITEM_FIELD, CFTopology.DEFAULT_RATING_FIELD), 5, ratings);

			// Create ratings stream
			Stream ratingStream = topology.newStream("ratings", ratingsSpout);

			// Create item recommendation query stream
			Stream recommendationQueryStream = topology.newDRPCStream("recommendedItems", localDRPC).each(new Fields("args"), new ExtractUser(), new Fields(CFTopology.DEFAULT_USER1_FIELD));

			// Create collaborative filtering topology with an in memory CF
			// state
			CFTopology cfTopology = new CFTopology(ratingStream, new InMemoryCFState.Factory());
			cfTopology.createRecommendationStream(recommendationQueryStream, 2, 2);

			// Submit and wait topology
			cluster.submitTopology(this.getClass().getSimpleName(), new Config(), topology.build());
			Thread.sleep(5000);

			List<Long> recommendedItems = extractRecommendedItems(localDRPC.execute("recommendedItems", "0"));
			assertTrue(recommendedItems.contains(6L));
			assertTrue(recommendedItems.contains(4L));
		} finally {
			cluster.shutdown();
			localDRPC.shutdown();
		}
	}

	/**
	 * Sorry for this! It could be better to use some REGEX
	 * 
	 * @param drpcResult
	 * @return
	 */
	protected static List<Long> extractRecommendedItems(String drpcResult) {
		//
		List<Long> recommendedItems = new ArrayList<Long>();

		int index = -1;
		do {
			index = drpcResult.indexOf("RecommendedItem [item=", index + 1);
			if (index != -1) {
				long item = Long.parseLong(drpcResult.substring(index + 22, index + 23));
				recommendedItems.add(item);
			}
		} while (index != -1);
		return recommendedItems;
	}

	private static class ExtractUsers extends BaseFunction {

		private static final long serialVersionUID = 7171566985006542069L;

		@Override
		public void execute(TridentTuple tuple, TridentCollector collector) {
			String[] args = tuple.getString(0).split(" ");
			long user1 = Long.parseLong(args[0]);
			long user2 = Long.parseLong(args[1]);
			collector.emit(new Values(user1, user2));
		}
	}

	private static class ExtractUser extends BaseFunction {

		private static final long serialVersionUID = 1863834953816610484L;

		@Override
		public void execute(TridentTuple tuple, TridentCollector collector) {
			String arg = tuple.getString(0);
			long user = Long.parseLong(arg);
			collector.emit(new Values(user));
		}
	}
}
