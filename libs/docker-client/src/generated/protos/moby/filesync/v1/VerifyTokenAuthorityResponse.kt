// Code generated by Wire protocol buffer compiler, do not edit.
// Source: moby.filesync.v1.VerifyTokenAuthorityResponse in github.com/moby/buildkit/session/auth/auth.proto
package moby.filesync.v1

import com.squareup.wire.FieldEncoding
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoReader
import com.squareup.wire.ProtoWriter
import com.squareup.wire.Syntax.PROTO_3
import com.squareup.wire.WireField
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
import kotlin.hashCode
import kotlin.jvm.JvmField
import okio.ByteString

public class VerifyTokenAuthorityResponse(
  @field:WireField(
    tag = 1,
    adapter = "com.squareup.wire.ProtoAdapter#BYTES",
    label = WireField.Label.OMIT_IDENTITY
  )
  public val Signed: ByteString = ByteString.EMPTY,
  unknownFields: ByteString = ByteString.EMPTY
) : Message<VerifyTokenAuthorityResponse, Nothing>(ADAPTER, unknownFields) {
  @Deprecated(
    message = "Shouldn't be used in Kotlin",
    level = DeprecationLevel.HIDDEN
  )
  public override fun newBuilder(): Nothing = throw AssertionError()

  public override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other !is VerifyTokenAuthorityResponse) return false
    if (unknownFields != other.unknownFields) return false
    if (Signed != other.Signed) return false
    return true
  }

  public override fun hashCode(): Int {
    var result = super.hashCode
    if (result == 0) {
      result = unknownFields.hashCode()
      result = result * 37 + Signed.hashCode()
      super.hashCode = result
    }
    return result
  }

  public override fun toString(): String {
    val result = mutableListOf<String>()
    result += """Signed=$Signed"""
    return result.joinToString(prefix = "VerifyTokenAuthorityResponse{", separator = ", ", postfix =
        "}")
  }

  public fun copy(Signed: ByteString = this.Signed, unknownFields: ByteString = this.unknownFields):
      VerifyTokenAuthorityResponse = VerifyTokenAuthorityResponse(Signed, unknownFields)

  public companion object {
    @JvmField
    public val ADAPTER: ProtoAdapter<VerifyTokenAuthorityResponse> = object :
        ProtoAdapter<VerifyTokenAuthorityResponse>(
      FieldEncoding.LENGTH_DELIMITED, 
      VerifyTokenAuthorityResponse::class, 
      "type.googleapis.com/moby.filesync.v1.VerifyTokenAuthorityResponse", 
      PROTO_3, 
      null
    ) {
      public override fun encodedSize(value: VerifyTokenAuthorityResponse): Int {
        var size = value.unknownFields.size
        if (value.Signed != ByteString.EMPTY) size += ProtoAdapter.BYTES.encodedSizeWithTag(1,
            value.Signed)
        return size
      }

      public override fun encode(writer: ProtoWriter, value: VerifyTokenAuthorityResponse): Unit {
        if (value.Signed != ByteString.EMPTY) ProtoAdapter.BYTES.encodeWithTag(writer, 1,
            value.Signed)
        writer.writeBytes(value.unknownFields)
      }

      public override fun decode(reader: ProtoReader): VerifyTokenAuthorityResponse {
        var Signed: ByteString = ByteString.EMPTY
        val unknownFields = reader.forEachTag { tag ->
          when (tag) {
            1 -> Signed = ProtoAdapter.BYTES.decode(reader)
            else -> reader.readUnknownField(tag)
          }
        }
        return VerifyTokenAuthorityResponse(
          Signed = Signed,
          unknownFields = unknownFields
        )
      }

      public override fun redact(value: VerifyTokenAuthorityResponse): VerifyTokenAuthorityResponse
          = value.copy(
        unknownFields = ByteString.EMPTY
      )
    }

    private const val serialVersionUID: Long = 0L
  }
}
