// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!
//
// Protofile syntax: PROTO3

package com.lmlt.actor.example.message



object MessageProto {
  private lazy val ProtoBytes: Array[Byte] =
      com.trueaccord.scalapb.Encoding.fromBase64(scala.collection.Seq(
  """Cg1tZXNzYWdlLnByb3RvEhZjb20ubG1sdC5hY3Rvci5leGFtcGxlIjEKBUZpcnN0EhQKBXNoYXJkGAEgASgJUgVzaGFyZBISC
  gRuYW1lGAIgASgJUgRuYW1lIgYKBExvb3AiHAoISGVsbG9FdnQSEAoDbnVtGAEgASgDUgNudW0iIgoKSGVsbG9TdGF0ZRIUCgVjb
  3VudBgBIAEoA1IFY291bnRiBnByb3RvMw=="""
      ).mkString)
  lazy val scalaDescriptor: _root_.scalapb.descriptors.FileDescriptor = {
    val scalaProto = com.google.protobuf.descriptor.FileDescriptorProto.parseFrom(ProtoBytes)
    _root_.scalapb.descriptors.FileDescriptor.buildFrom(scalaProto, Seq(
    ))
  }
  lazy val javaDescriptor: com.google.protobuf.Descriptors.FileDescriptor = {
    val javaProto = com.google.protobuf.DescriptorProtos.FileDescriptorProto.parseFrom(ProtoBytes)
    com.google.protobuf.Descriptors.FileDescriptor.buildFrom(javaProto, Array(
    ))
  }
  @deprecated("Use javaDescriptor instead. In a future version this will refer to scalaDescriptor.", "ScalaPB 0.5.47")
  def descriptor: com.google.protobuf.Descriptors.FileDescriptor = javaDescriptor
}