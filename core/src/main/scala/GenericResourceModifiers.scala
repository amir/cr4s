package cr4s

import com.softwaremill.quicklens._
import shapeless._
import shapeless.ops.record._
import skuber.{ ObjectMeta, ObjectResource, OwnerReference }

object GenericResourceModifiers {
  type MetadataModifier[R <: HList] = Modifier.Aux[R, Witness.`'metadata`.T, ObjectMeta, ObjectMeta, R]

  def withMetadata[O <: ObjectResource, R <: HList](obj: O, metadata: ObjectMeta)(implicit
                                                                                  generic: LabelledGeneric.Aux[O, R],
                                                                                  modifier: MetadataModifier[R]): O = {
    val rec = generic.to(obj)
    val updatedRec = modifier(rec, _ => metadata)
    generic.from(updatedRec)
  }

  def addOwner[O <: ObjectResource, R <: HList](obj: O, newOwner: OwnerReference)(implicit
                                                                                  generic: LabelledGeneric.Aux[O, R],
                                                                                  modifier: MetadataModifier[R]): O = {

    val filteredOwnerReferences =
      obj.metadata.ownerReferences.filterNot(or => or.kind == newOwner.kind && or.uid == newOwner.uid)

    val updatedOwnerReferences = newOwner :: filteredOwnerReferences
    withMetadata(obj, obj.metadata.copy(ownerReferences = updatedOwnerReferences))
  }
}
