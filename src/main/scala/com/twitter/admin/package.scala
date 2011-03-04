/*
 * Copyright 2011 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter

package object admin {
  // AdminHttpService.scala
  type CustomHttpHandler = com.twitter.ostrich.admin.CustomHttpHandler
  type MissingFileHandler = com.twitter.ostrich.admin.MissingFileHandler
  type PageResourceHandler = com.twitter.ostrich.admin.PageResourceHandler
  type FolderResourceHandler = com.twitter.ostrich.admin.FolderResourceHandler
  val CgiRequestHandler = com.twitter.ostrich.admin.CgiRequestHandler
  type CgiRequestHandler = com.twitter.ostrich.admin.CgiRequestHandler
  type HeapResourceHandler = com.twitter.ostrich.admin.HeapResourceHandler
  type CommandRequestHandler = com.twitter.ostrich.admin.CommandRequestHandler
  type AdminHttpService = com.twitter.ostrich.admin.AdminHttpService

  // BackgroundProcess.scala
  type BackgroundProcess = com.twitter.ostrich.admin.BackgroundProcess
  val BackgroundProcess = com.twitter.ostrich.admin.BackgroundProcess
  type PeriodicBackgroundProcess = com.twitter.ostrich.admin.PeriodicBackgroundProcess

  // CommandHandler.scala
  type UnknownCommandError = com.twitter.ostrich.admin.UnknownCommandError
  type CommandHandler = com.twitter.ostrich.admin.CommandHandler

  // Heapster.scala
  type Heapster = com.twitter.ostrich.admin.Heapster
  val Heapster = com.twitter.ostrich.admin.Heapster

  // RuntimeEnvironment.scala
  type RuntimeEnvironment = com.twitter.ostrich.admin.RuntimeEnvironment
  val RuntimeEnvironment = com.twitter.ostrich.admin.RuntimeEnvironment

  // Service.scala
  type Service = com.twitter.ostrich.admin.Service

  // ServiceTracker.scala
  val ServiceTracker = com.twitter.ostrich.admin.ServiceTracker

  // TimeSeriesCollector.scala
  type TimeSeriesCollector = com.twitter.ostrich.admin.TimeSeriesCollector
}
