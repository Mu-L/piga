package api

import java.time.{LocalDate, LocalTime}

import api.PicklableDbQuery.FieldWithValue
import boopickle.Default._
import common.time.LocalDateTime
import models.Entity
import models.access.ModelField
import models.modification.EntityType._
import models.modification.{EntityModification, EntityType}
import models.user.User

object Picklers {

  // Pickler that does the same as an autogenerated User pickler, except that it redacts the user's password
  implicit object UserPickler extends Pickler[User] {
    override def pickle(user: User)(implicit state: PickleState): Unit = logExceptions {
      state.pickle(user.loginName)
      // Password redacted
      state.pickle(user.name)
      state.pickle(user.isAdmin)
      state.pickle(user.idOption)
    }
    override def unpickle(implicit state: UnpickleState): User = logExceptions {
      User(
        loginName = state.unpickle[String],
        passwordHash = "<redacted>",
        name = state.unpickle[String],
        isAdmin = state.unpickle[Boolean],
        idOption = state.unpickle[Option[Long]]
      )
    }
  }

  implicit object LocalDateTimePickler extends Pickler[LocalDateTime] {
    override def pickle(dateTime: LocalDateTime)(implicit state: PickleState): Unit = logExceptions {
      val date = dateTime.toLocalDate
      val time = dateTime.toLocalTime

      state.pickle(date.getYear)
      state.pickle(date.getMonth.getValue)
      state.pickle(date.getDayOfMonth)
      state.pickle(time.getHour)
      state.pickle(time.getMinute)
      state.pickle(time.getSecond)
    }
    override def unpickle(implicit state: UnpickleState): LocalDateTime = logExceptions {
      LocalDateTime.of(
        LocalDate.of(
          state.unpickle[Int] /* year */,
          state.unpickle[Int] /* month */,
          state.unpickle[Int] /* dayOfMonth */
        ),
        LocalTime.of(
          state.unpickle[Int] /* hour */,
          state.unpickle[Int] /* minute */,
          state.unpickle[Int] /* second */
        )
      )
    }
  }

  implicit object EntityTypePickler extends Pickler[EntityType.any] {
    override def pickle(entityType: EntityType.any)(implicit state: PickleState): Unit = logExceptions {
      val intValue: Int = entityType match {
        case UserType                    => 1
      }
      state.pickle(intValue)
    }
    override def unpickle(implicit state: UnpickleState): EntityType.any = logExceptions {
      state.unpickle[Int] match {
        case 1 => UserType
      }
    }
  }

  implicit val entityPickler = compositePickler[Entity]
    .addConcreteType[User]

  implicit object EntityModificationPickler extends Pickler[EntityModification] {
    val addNumber = 1
    val updateNumber = 3
    val removeNumber = 2

    override def pickle(modification: EntityModification)(implicit state: PickleState): Unit =
      logExceptions {
        state.pickle[EntityType.any](modification.entityType)
        // Pickle number
        state.pickle(modification match {
          case _: EntityModification.Add[_]    => addNumber
          case _: EntityModification.Update[_] => updateNumber
          case _: EntityModification.Remove[_] => removeNumber
        })
        modification match {
          case EntityModification.Add(entity)      => state.pickle(entity)
          case EntityModification.Update(entity)   => state.pickle(entity)
          case EntityModification.Remove(entityId) => state.pickle(entityId)
        }
      }
    override def unpickle(implicit state: UnpickleState): EntityModification = logExceptions {
      val entityType = state.unpickle[EntityType.any]
      state.unpickle[Int] match {
        case `addNumber` =>
          val entity = state.unpickle[Entity]
          def addModification[E <: Entity](entity: Entity, entityType: EntityType[E]): EntityModification = {
            EntityModification.Add(entityType.checkRightType(entity))(entityType)
          }
          addModification(entity, entityType)
        case `updateNumber` =>
          val entity = state.unpickle[Entity]
          def updateModification[E <: Entity](entity: Entity,
                                              entityType: EntityType[E]): EntityModification = {
            EntityModification.Update(entityType.checkRightType(entity))(entityType)
          }
          updateModification(entity, entityType)
        case `removeNumber` =>
          val entityId = state.unpickle[Long]
          EntityModification.Remove(entityId)(entityType)
      }
    }
  }

  implicit val fieldWithValuePickler: Pickler[FieldWithValue] =
    new Pickler[FieldWithValue] {
      override def pickle(obj: FieldWithValue)(implicit state: PickleState) = {
        def internal[E]: Unit = {
          state.pickle(obj.field)
          state.pickle(obj.value.asInstanceOf[E])(
            picklerForField(obj.field.toRegular).asInstanceOf[Pickler[E]])
        }
        internal
      }
      override def unpickle(implicit state: UnpickleState) = {
        def internal[E]: FieldWithValue = {
          val field = state.unpickle[PicklableModelField]
          val value = state.unpickle[E](picklerForField(field.toRegular).asInstanceOf[Pickler[E]])
          FieldWithValue(field = field, value = value)
        }
        internal
      }

      private def picklerForField(field: ModelField[_, _]): Pickler[_] = {
        def fromType[V: Pickler](fieldType: ModelField.FieldType[V]): Pickler[V] = implicitly
        field.fieldType match {
          case ModelField.FieldType.BooleanType       => fromType(ModelField.FieldType.BooleanType)
          case ModelField.FieldType.LongType          => fromType(ModelField.FieldType.LongType)
          case ModelField.FieldType.DoubleType        => fromType(ModelField.FieldType.DoubleType)
          case ModelField.FieldType.StringType        => fromType(ModelField.FieldType.StringType)
          case ModelField.FieldType.LocalDateTimeType => fromType(ModelField.FieldType.LocalDateTimeType)
          case ModelField.FieldType.StringSeqType     => fromType(ModelField.FieldType.StringSeqType)
        }
      }
    }

  implicit val picklableDbQueryPickler: Pickler[PicklableDbQuery] = {
    implicit val fieldWithDirectionPickler: Pickler[PicklableDbQuery.Sorting.FieldWithDirection] =
      boopickle.Default.generatePickler
    implicit val sortingPickler: Pickler[PicklableDbQuery.Sorting] = boopickle.Default.generatePickler
    boopickle.Default.generatePickler
  }

  private def logExceptions[T](codeBlock: => T): T = {
    try {
      codeBlock
    } catch {
      case t: Throwable =>
        println(s"  Caught exception while pickling: $t")
        t.printStackTrace()
        throw t
    }
  }
}
