/**
 * Copyright (C) <2020>  <mycat>
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If
 * not, see <http://www.gnu.org/licenses/>.
 */
package io.mycat.router.mycat1xfunction;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import io.mycat.router.ShardingTableHandler;
import io.mycat.router.Mycat1xSingleValueRuleFunction;

import java.util.*;
import java.util.Map.Entry;

public class PartitionByMurmurHash extends Mycat1xSingleValueRuleFunction {

  private static final int DEFAULT_WEIGHT = 1;
  private final SortedMap<Integer, Integer> bucketMap = new TreeMap<>();
  private HashFunction hash;
  private int count;

  private static int getWeight(Map<Integer, Integer> weightMap, int bucket) {
    Integer w = weightMap.get(bucket);
    if (w == null) {
      w = DEFAULT_WEIGHT;
    }
    return w;
  }

  @Override
  public String name() {
    return "PartitionByMurmurHash";
  }

  @Override
  public int calculateIndex(String columnValue) {
    SortedMap<Integer, Integer> tail = bucketMap
        .tailMap(hash.hashUnencodedChars(columnValue).asInt());
    if (tail.isEmpty()) {
      return bucketMap.get(bucketMap.firstKey());
    }
    return tail.get(tail.firstKey());
  }

  @Override
  public int[] calculateIndexRange(String beginValue, String endValue) {
    return null;
  }



  @Override
  public void init(ShardingTableHandler table, Map<String, Object> prot, Map<String, Object> ranges) {
    int seed = Integer.parseInt(Objects.toString(prot.get("seed")));
    this.count = Integer.parseInt(Objects.toString(prot.get("count")));
    int virtualBucketTimes = Integer.parseInt(Objects.toString(prot.get("virtualBucketTimes")));
    initBucketMap(ranges, seed, count, virtualBucketTimes);
  }

  private void initBucketMap(Map<String, Object> ranges, int seed, int count,
      int virtualBucketTimes) {
    Map<Integer, Integer> weightMap = new HashMap<>();
    for (Entry<String, Object> entry : ranges.entrySet()) {
      String key = entry.getKey();
      String value =Objects.toString(entry.getValue());
      int weight = Integer.parseInt(value);
      weightMap.put(Integer.parseInt(key), weight > 0 ? weight : 1);
    }

    hash = Hashing.murmur3_32(seed);//计算一致性哈希的对象
    for (int i = 0; i < count; i++) {//构造一致性哈希环，用TreeMap表示
      StringBuilder hashName = new StringBuilder("SHARD-").append(i);
      for (int n = 0, shard = virtualBucketTimes * getWeight(weightMap, i); n < shard; n++) {
        bucketMap.put(hash.hashUnencodedChars(hashName.append("-NODE-").append(n)).asInt(), i);
      }
    }
  }
}