// Code generated by Wire protocol buffer compiler, do not edit.
// Source: moby.buildkit.v1.StatusResponse in github.com/moby/buildkit/api/services/control/control.proto
package moby.buildkit.v1

import com.squareup.wire.FieldEncoding
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoReader
import com.squareup.wire.ProtoWriter
import com.squareup.wire.Syntax.PROTO_3
import com.squareup.wire.WireField
import com.squareup.wire.`internal`.immutableCopyOf
import com.squareup.wire.`internal`.redactElements
import kotlin.Any
import kotlin.AssertionError
import kotlin.Boolean
import kotlin.Deprecated
import kotlin.DeprecationLevel
import kotlin.Int
import kotlin.Long
import kotlin.Nothing
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.jvm.JvmField
import okio.ByteString

public class StatusResponse(
  vertexes: List<Vertex> = emptyList(),
  statuses: List<VertexStatus> = emptyList(),
  logs: List<VertexLog> = emptyList(),
  unknownFields: ByteString = ByteString.EMPTY
) : Message<StatusResponse, Nothing>(ADAPTER, unknownFields) {
  @field:WireField(
    tag = 1,
    adapter = "moby.buildkit.v1.Vertex#ADAPTER",
    label = WireField.Label.REPEATED
  )
  public val vertexes: List<Vertex> = immutableCopyOf("vertexes", vertexes)

  @field:WireField(
    tag = 2,
    adapter = "moby.buildkit.v1.VertexStatus#ADAPTER",
    label = WireField.Label.REPEATED
  )
  public val statuses: List<VertexStatus> = immutableCopyOf("statuses", statuses)

  @field:WireField(
    tag = 3,
    adapter = "moby.buildkit.v1.VertexLog#ADAPTER",
    label = WireField.Label.REPEATED
  )
  public val logs: List<VertexLog> = immutableCopyOf("logs", logs)

  @Deprecated(
    message = "Shouldn't be used in Kotlin",
    level = DeprecationLevel.HIDDEN
  )
  public override fun newBuilder(): Nothing = throw AssertionError()

  public override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other !is StatusResponse) return false
    if (unknownFields != other.unknownFields) return false
    if (vertexes != other.vertexes) return false
    if (statuses != other.statuses) return false
    if (logs != other.logs) return false
    return true
  }

  public override fun hashCode(): Int {
    var result = super.hashCode
    if (result == 0) {
      result = unknownFields.hashCode()
      result = result * 37 + vertexes.hashCode()
      result = result * 37 + statuses.hashCode()
      result = result * 37 + logs.hashCode()
      super.hashCode = result
    }
    return result
  }

  public override fun toString(): String {
    val result = mutableListOf<String>()
    if (vertexes.isNotEmpty()) result += """vertexes=$vertexes"""
    if (statuses.isNotEmpty()) result += """statuses=$statuses"""
    if (logs.isNotEmpty()) result += """logs=$logs"""
    return result.joinToString(prefix = "StatusResponse{", separator = ", ", postfix = "}")
  }

  public fun copy(
    vertexes: List<Vertex> = this.vertexes,
    statuses: List<VertexStatus> = this.statuses,
    logs: List<VertexLog> = this.logs,
    unknownFields: ByteString = this.unknownFields
  ): StatusResponse = StatusResponse(vertexes, statuses, logs, unknownFields)

  public companion object {
    @JvmField
    public val ADAPTER: ProtoAdapter<StatusResponse> = object : ProtoAdapter<StatusResponse>(
      FieldEncoding.LENGTH_DELIMITED, 
      StatusResponse::class, 
      "type.googleapis.com/moby.buildkit.v1.StatusResponse", 
      PROTO_3, 
      null
    ) {
      public override fun encodedSize(value: StatusResponse): Int {
        var size = value.unknownFields.size
        size += Vertex.ADAPTER.asRepeated().encodedSizeWithTag(1, value.vertexes)
        size += VertexStatus.ADAPTER.asRepeated().encodedSizeWithTag(2, value.statuses)
        size += VertexLog.ADAPTER.asRepeated().encodedSizeWithTag(3, value.logs)
        return size
      }

      public override fun encode(writer: ProtoWriter, value: StatusResponse): Unit {
        Vertex.ADAPTER.asRepeated().encodeWithTag(writer, 1, value.vertexes)
        VertexStatus.ADAPTER.asRepeated().encodeWithTag(writer, 2, value.statuses)
        VertexLog.ADAPTER.asRepeated().encodeWithTag(writer, 3, value.logs)
        writer.writeBytes(value.unknownFields)
      }

      public override fun decode(reader: ProtoReader): StatusResponse {
        val vertexes = mutableListOf<Vertex>()
        val statuses = mutableListOf<VertexStatus>()
        val logs = mutableListOf<VertexLog>()
        val unknownFields = reader.forEachTag { tag ->
          when (tag) {
            1 -> vertexes.add(Vertex.ADAPTER.decode(reader))
            2 -> statuses.add(VertexStatus.ADAPTER.decode(reader))
            3 -> logs.add(VertexLog.ADAPTER.decode(reader))
            else -> reader.readUnknownField(tag)
          }
        }
        return StatusResponse(
          vertexes = vertexes,
          statuses = statuses,
          logs = logs,
          unknownFields = unknownFields
        )
      }

      public override fun redact(value: StatusResponse): StatusResponse = value.copy(
        vertexes = value.vertexes.redactElements(Vertex.ADAPTER),
        statuses = value.statuses.redactElements(VertexStatus.ADAPTER),
        logs = value.logs.redactElements(VertexLog.ADAPTER),
        unknownFields = ByteString.EMPTY
      )
    }

    private const val serialVersionUID: Long = 0L
  }
}
