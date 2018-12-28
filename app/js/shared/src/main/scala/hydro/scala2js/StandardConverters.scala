package hydro.scala2js

import java.time.{LocalDate, LocalTime}

import app.models._
import app.models.access.ModelField
import app.models.document.{DocumentEntity, TaskEntity}
import app.models.modification._
import app.models.user.User
import common.GuavaReplacement.ImmutableBiMap
import common.OrderToken
import hydro.common.time.LocalDateTime
import hydro.scala2js.Scala2Js.{Converter, MapConverter}

import scala.collection.immutable.Seq
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object StandardConverters {

  // **************** Convertor generators **************** //
  implicit def fromEntityType[E <: Entity: EntityType]: MapConverter[E] = {
    val entityType: EntityType[E] = implicitly[EntityType[E]]
    val converter: MapConverter[_ <: Entity] = entityType match {
      case EntityType.UserType           => UserConverter
      case EntityType.DocumentEntityType => DocumentEntityConverter
      case EntityType.TaskEntityType     => TaskEntityConverter
    }
    converter.asInstanceOf[MapConverter[E]]
  }

  def fromModelField[V](modelField: ModelField[V, _]): Converter[V] = {
    def fromType[V2: Converter](fieldType: ModelField.FieldType[V2]): Converter[V2] = implicitly
    val result = modelField.fieldType match {
      case ModelField.FieldType.BooleanType       => fromType(ModelField.FieldType.BooleanType)
      case ModelField.FieldType.IntType           => fromType(ModelField.FieldType.IntType)
      case ModelField.FieldType.MaybeIntType      => fromType(ModelField.FieldType.MaybeIntType)
      case ModelField.FieldType.LongType          => fromType(ModelField.FieldType.LongType)
      case ModelField.FieldType.MaybeLongType     => fromType(ModelField.FieldType.MaybeLongType)
      case ModelField.FieldType.DoubleType        => fromType(ModelField.FieldType.DoubleType)
      case ModelField.FieldType.StringType        => fromType(ModelField.FieldType.StringType)
      case ModelField.FieldType.LocalDateTimeType => fromType(ModelField.FieldType.LocalDateTimeType)
      case ModelField.FieldType.MaybeLocalDateTimeType =>
        fromType(ModelField.FieldType.MaybeLocalDateTimeType)
      case ModelField.FieldType.FiniteDurationType => fromType(ModelField.FieldType.FiniteDurationType)
      case ModelField.FieldType.StringSeqType     => fromType(ModelField.FieldType.StringSeqType)
      case ModelField.FieldType.OrderTokenType    => fromType(ModelField.FieldType.OrderTokenType)
    }
    result.asInstanceOf[Converter[V]]
  }

  def enumConverter[T](values: T*): Converter[T] = {
    val valueToNumber: ImmutableBiMap[T, Int] = {
      val builder = ImmutableBiMap.builder[T, Int]()
      for ((value, number) <- values.zipWithIndex) {
        builder.put(value, number)
      }
      builder.build()
    }

    new Converter[T] {
      override def toJs(value: T) = Scala2Js.toJs(valueToNumber.get(value))
      override def toScala(value: js.Any) = valueToNumber.inverse().get(Scala2Js.toScala[Int](value))
    }
  }

  implicit def seqConverter[A: Converter]: Converter[Seq[A]] =
    new Converter[Seq[A]] {
      override def toJs(seq: Seq[A]) =
        seq.toStream.map(Scala2Js.toJs[A]).toJSArray
      override def toScala(value: js.Any) =
        value.asInstanceOf[js.Array[js.Any]].toStream.map(Scala2Js.toScala[A]).toVector
    }

  implicit def optionConverter[V: Converter]: Converter[Option[V]] =
    new Converter[Option[V]] {
      override def toJs(option: Option[V]) = option match {
        case Some(v) => Scala2Js.toJs(v)
        case None    => null
      }
      override def toScala(value: js.Any) = {
        if (value == null) {
          None
        } else {
          Some(Scala2Js.toScala[V](value))
        }
      }
    }

  // **************** General converters **************** //
  implicit object NullConverter extends Converter[js.Any] {
    override def toJs(obj: js.Any) = obj
    override def toScala(obj: js.Any) = obj
  }

  implicit object StringConverter extends Converter[String] {
    override def toJs(string: String) = string
    override def toScala(value: js.Any) = value.asInstanceOf[String]
  }

  implicit object BooleanConverter extends Converter[Boolean] {
    override def toJs(bool: Boolean) = bool
    override def toScala(value: js.Any) = value.asInstanceOf[Boolean]
  }

  implicit object IntConverter extends Converter[Int] {
    override def toJs(int: Int) = int
    override def toScala(value: js.Any) = value.asInstanceOf[Int]
  }

  implicit object LongConverter extends Converter[Long] {
    override def toJs(long: Long) = {
      // Note: It would be easier to implement this by `"%022d".format(long)`
      // but that transforms the given long to a javascript number (double precision)
      // causing the least significant long digits sometimes to become zero
      // (e.g. 6886911427549585292 becomes 6886911427549585000)
      val signChar = if (long < 0) "-" else ""
      val stringWithoutSign = Math.abs(long).toString

      val numZerosToPrepend = 22 - stringWithoutSign.size
      require(numZerosToPrepend > 0)
      signChar + ("0" * numZerosToPrepend) + stringWithoutSign
    }
    override def toScala(value: js.Any) = value.asInstanceOf[String].toLong
  }

  implicit object DoubleConverter extends Converter[Double] {
    override def toJs(double: Double) = double
    override def toScala(value: js.Any) = value.asInstanceOf[Double]
  }

  implicit object LocalDateTimeConverter extends Converter[LocalDateTime] {

    private val secondsInDay = 60 * 60 * 24

    override def toJs(dateTime: LocalDateTime) = {
      val epochDay = dateTime.toLocalDate.toEpochDay.toInt
      val secondOfDay = dateTime.toLocalTime.toSecondOfDay
      epochDay * secondsInDay + secondOfDay
    }
    override def toScala(value: js.Any) = {
      val combinedInt = value.asInstanceOf[Int]
      val epochDay = combinedInt / secondsInDay
      val secondOfDay = combinedInt % secondsInDay
      LocalDateTime.of(LocalDate.ofEpochDay(epochDay), LocalTime.ofSecondOfDay(secondOfDay))
    }
  }

  implicit object OrderTokenConverter extends Converter[OrderToken] {
    override def toJs(token: OrderToken) = {
      token.parts.toJSArray
    }
    override def toScala(value: js.Any) = {
      OrderToken(value.asInstanceOf[js.Array[Int]].toList)
    }
  }

  implicit val EntityTypeConverter: Converter[EntityType.any] =
    enumConverter(EntityType.UserType, EntityType.DocumentEntityType, EntityType.TaskEntityType)

  implicit object EntityModificationConverter extends Converter[EntityModification] {
    private val addNumber: Int = 1
    private val updateNumber: Int = 2
    private val removeNumber: Int = 3

    override def toJs(modification: EntityModification) = {
      def internal[E <: Entity] = {
        val result = js.Array[js.Any]()

        result.push(Scala2Js.toJs[EntityType.any](modification.entityType))
        modification match {
          case EntityModification.Add(entity) =>
            result.push(addNumber)
            result.push(
              Scala2Js.toJs(entity.asInstanceOf[E])(
                fromEntityType(modification.entityType.asInstanceOf[EntityType[E]])))
          case EntityModification.Update(entity) =>
            result.push(updateNumber)
            result.push(
              Scala2Js.toJs(entity.asInstanceOf[E])(
                fromEntityType(modification.entityType.asInstanceOf[EntityType[E]])))
          case EntityModification.Remove(entityId) =>
            result.push(removeNumber)
            result.push(Scala2Js.toJs(entityId))
        }

        result
      }
      internal
    }

    override def toScala(value: js.Any) = {
      def internal[E <: Entity] = {
        val array = value.asInstanceOf[js.Array[js.Any]]
        implicit val entityType = Scala2Js.toScala[EntityType.any](array.shift()).asInstanceOf[EntityType[E]]
        val modificationTypeNumber = Scala2Js.toScala[Int](array.shift())

        array.toVector match {
          case Vector(entity) if modificationTypeNumber == addNumber =>
            EntityModification.Add(Scala2Js.toScala[E](entity))
          case Vector(entity) if modificationTypeNumber == updateNumber =>
            EntityModification.Update(Scala2Js.toScala[E](entity))
          case Vector(entityId) if modificationTypeNumber == removeNumber =>
            EntityModification.Remove(Scala2Js.toScala[Long](entityId))(entityType)
        }
      }
      internal
    }
  }

  // **************** Entity converters **************** //
  final class EntityConverter[E <: Entity: EntityType](allFieldsWithoutId: Seq[ModelField[_, E]] = Seq(),
                                                       toScalaWithoutId: EntityConverter.DictWrapper[E] => E)
      extends MapConverter[E] {
    override def toJs(entity: E) = {
      val result = js.Dictionary[js.Any]()

      def addField[V](field: ModelField[V, E]): Unit = {
        result.update(field.name, Scala2Js.toJs(field.get(entity), field))
      }
      for (field <- allFieldsWithoutId) {
        addField(field)
      }
      for (id <- entity.idOption) {
        result.update(ModelField.id[E].name, Scala2Js.toJs(id, ModelField.id[E]))
      }
      result
    }

    override def toScala(dict: js.Dictionary[js.Any]) = {
      val entityWithoutId = toScalaWithoutId(new EntityConverter.DictWrapper(dict))
      val idOption = dict.get(ModelField.id[E].name).map(Scala2Js.toScala[Long])
      if (idOption.isDefined) {
        Entity.withId(idOption.get, entityWithoutId)
      } else {
        entityWithoutId
      }
    }
  }
  object EntityConverter {
    final class DictWrapper[E <: Entity: EntityType](val dict: js.Dictionary[js.Any]) {
      def getRequired[V](field: ModelField[V, E]): V = {
        require(dict.contains(field.name), s"Key ${field.name} is missing from ${js.JSON.stringify(dict)}")
        Scala2Js.toScala[V](dict(field.name))(fromModelField(field))
      }
    }
  }

  implicit val UserConverter: EntityConverter[User] = new EntityConverter(
    allFieldsWithoutId = Seq(
      ModelField.User.loginName,
      ModelField.User.passwordHash,
      ModelField.User.name,
      ModelField.User.isAdmin,
    ),
    toScalaWithoutId = dict =>
      User(
        loginName = dict.getRequired(ModelField.User.loginName),
        passwordHash = dict.getRequired(ModelField.User.passwordHash),
        name = dict.getRequired(ModelField.User.name),
        isAdmin = dict.getRequired(ModelField.User.isAdmin)
    )
  )

  implicit val DocumentEntityConverter: EntityConverter[DocumentEntity] = new EntityConverter(
    allFieldsWithoutId = Seq(
      ModelField.DocumentEntity.name,
      ModelField.DocumentEntity.orderToken,
    ),
    toScalaWithoutId = dict =>
      DocumentEntity(
        name = dict.getRequired(ModelField.DocumentEntity.name),
        orderToken = dict.getRequired(ModelField.DocumentEntity.orderToken))
  )

  implicit val TaskEntityConverter: EntityConverter[TaskEntity] = new EntityConverter(
    allFieldsWithoutId = Seq(
      ModelField.TaskEntity.documentId,
      ModelField.TaskEntity.contentHtml,
      ModelField.TaskEntity.orderToken,
      ModelField.TaskEntity.indentation,
      ModelField.TaskEntity.collapsed,
      ModelField.TaskEntity.delayedUntil,
      ModelField.TaskEntity.tags,
    ),
    toScalaWithoutId = dict =>
      TaskEntity(
        documentId = dict.getRequired(ModelField.TaskEntity.documentId),
        contentHtml = dict.getRequired(ModelField.TaskEntity.contentHtml),
        orderToken = dict.getRequired(ModelField.TaskEntity.orderToken),
        indentation = dict.getRequired(ModelField.TaskEntity.indentation),
        collapsed = dict.getRequired(ModelField.TaskEntity.collapsed),
        delayedUntil = dict.getRequired(ModelField.TaskEntity.delayedUntil),
        tags = dict.getRequired(ModelField.TaskEntity.tags)
    )
  )
}
