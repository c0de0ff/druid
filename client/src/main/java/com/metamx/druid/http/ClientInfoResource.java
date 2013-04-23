/*
 * Druid - a distributed column store.
 * Copyright (C) 2012  Metamarkets Group Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.metamx.druid.http;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.metamx.druid.client.DataSegment;
import com.metamx.druid.client.DruidDataSource;
import com.metamx.druid.client.DruidServer;
import com.metamx.druid.client.ServerInventoryThingie;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 */
@Path("/druid/v2/datasources")
public class ClientInfoResource
{
  private static final int SEGMENT_HISTORY_MILLIS = 7 * 24 * 60 * 60 * 1000; // ONE WEEK

  private ServerInventoryThingie serverInventoryThingie;

  @Inject
  public ClientInfoResource(
      ServerInventoryThingie serverInventoryThingie
  )
  {
    this.serverInventoryThingie = serverInventoryThingie;
  }

  private Map<String, List<DataSegment>> getSegmentsForDatasources()
  {
    final Map<String, List<DataSegment>> dataSourceMap = Maps.newHashMap();
    for (DruidServer server : serverInventoryThingie.getInventory()) {
      for (DruidDataSource dataSource : server.getDataSources()) {
        if (!dataSourceMap.containsKey(dataSource.getName())) {
          dataSourceMap.put(dataSource.getName(), Lists.<DataSegment>newArrayList());
        }
        List<DataSegment> segments = dataSourceMap.get(dataSource.getName());
        segments.addAll(dataSource.getSegments());
      }
    }
    return dataSourceMap;
  }

  @GET
  @Produces("application/json")
  public Iterable<String> getDataSources()
  {
    return getSegmentsForDatasources().keySet();
  }

  @GET
  @Path("/{dataSourceName}")
  @Produces("application/json")
  public Map<String, Object> getDatasource(
      @PathParam("dataSourceName") String dataSourceName,
      @QueryParam("interval") String interval
  )
  {
    return ImmutableMap.<String, Object>of(
        "dimensions", getDatasourceDimensions(dataSourceName, interval),
        "metrics", getDatasourceMetrics(dataSourceName, interval)
    );
  }

  @GET
  @Path("/{dataSourceName}/dimensions")
  @Produces("application/json")
  public Iterable<String> getDatasourceDimensions(
      @PathParam("dataSourceName") String dataSourceName,
      @QueryParam("interval") String interval
  )
  {
    List<DataSegment> segments = getSegmentsForDatasources().get(dataSourceName);

    Interval theInterval;
    if (interval == null || interval.isEmpty()) {
      DateTime now = new DateTime();
      theInterval = new Interval(now.minusMillis(SEGMENT_HISTORY_MILLIS), now);
    } else {
      theInterval = new Interval(interval);
    }

    Set<String> dims = Sets.newHashSet();
    for (DataSegment segment : segments) {
      if (theInterval.overlaps(segment.getInterval())) {
        dims.addAll(segment.getDimensions());
      }
    }

    return dims;
  }

  @GET
  @Path("/{dataSourceName}/metrics")
  @Produces("application/json")
  public Iterable<String> getDatasourceMetrics(
      @PathParam("dataSourceName") String dataSourceName,
      @QueryParam("interval") String interval
  )
  {
    List<DataSegment> segments = getSegmentsForDatasources().get(dataSourceName);

    Interval theInterval;
    if (interval == null || interval.isEmpty()) {
      DateTime now = new DateTime();
      theInterval = new Interval(now.minusMillis(SEGMENT_HISTORY_MILLIS), now);
    } else {
      theInterval = new Interval(interval);
    }

    Set<String> metrics = Sets.newHashSet();
    for (DataSegment segment : segments) {
      if (theInterval.overlaps(segment.getInterval())) {
        metrics.addAll(segment.getMetrics());
      }
    }

    return metrics;
  }
}
