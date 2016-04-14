/*
 * Copyright 2015
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package influxdbreporter.core

import com.codahale.metrics.Clock
import influxdbreporter.core.collectors.MetricCollector
import influxdbreporter.core.metrics.{MetricByTag, Metric}
import influxdbreporter.core.metrics.Metric.CodehaleMetric
import influxdbreporter.core.utils.UtcClock
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

class InfluxdbReporter[S](registry: MetricRegistry,
                          writer: Writer[S],
                          client: MetricClient[S],
                          interval: FiniteDuration,
                          batcher: Batcher[S] = new InfluxBatcher[S],
                          cache: Option[WriterDataCache[S]] = None,
                          clock: Clock = UtcClock)
                         (implicit executionContext: ExecutionContext)
  extends ScheduledReporter[S](registry, interval, batcher, cache) {

  def withInterval(newInterval: FiniteDuration): InfluxdbReporter[S] =
    new InfluxdbReporter[S](registry, writer, client, newInterval, batcher, cache, clock)

  override protected def collectMetrics[M <: CodehaleMetric](metrics: Map[String, (Metric[M], MetricCollector[M])]): Future[List[WriterData[S]]] = {
    val timestamp = clock.getTick
    Future.sequence(metrics.toList.map {
      case (name, (metric, collector)) =>
        metric.popMetrics.map {
          _.map {
            case MetricByTag(tags, m) =>
              collector.collect(writer, name, m, timestamp, tags: _*)
          }
        }
    }).map(listOfLists => listOfLists.flatten)
  }

  override protected def reportMetrics(collectedMetricsData: List[WriterData[S]]): Future[Boolean] = {
    client.sendData(collectedMetricsData)
  }

}