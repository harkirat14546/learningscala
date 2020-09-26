package com.mediakind.mediafirst.spark.performance.reports.scalalearning

abstract class Vehicle(name: String)

case class Bike(name: String) extends Vehicle(name) {
  override def toString: String = name
}

case class Car(name: String) extends Vehicle(name) {
}


trait VehicleDatabaseService[A <: Vehicle] {
  protected def addOrUpdate(ad: Vehicle) = {
    println(s"Adding or updating vehicle = $ad")
  }

  protected def get(ge: Vehicle) = {
    println(s"Getting vehicle = $ge")
  }

  protected def remove(re: Vehicle) = {
    println(re)
  }
}


trait VehicleInventory[T <: Vehicle] {
  def create(cr: Vehicle) = println("bye")

  def read(re: Vehicle) = {}

  def update(up: Vehicle) = {}

  def delete(de: Vehicle) = {}
}

class VehicleInventorySystem[A <: Vehicle] extends VehicleInventory[A]
  with VehicleDatabaseService[A] {
  override def create(cr: Vehicle): Unit = {
    println(s"Create vehicle $cr")
    addOrUpdate(cr)
  }

  override def read(re: Vehicle): Unit = {
    println(s"Read vehicle = $re")
    get(re)

  }

  override def update(up: Vehicle): Unit = println(up)

  override def delete(de: Vehicle): Unit = super.delete(de)
}

trait VehicleSystem[A <: Vehicle] {
  val vehicleInventorySystem: VehicleInventorySystem[Vehicle]

  def checkVehicleStock(be: A): Unit = {
    vehicleInventorySystem.read(be)
  }
}

class VehicleInventorService[A <: Vehicle] {
  def checkStock(che: A): Unit = {
    println(s"checking stock for vehicle = $che")
  }
}

class VehiclePricingService[A <: Vehicle] {
  def checkPrice(che: A): Unit = {
    println(s"checking price for vehicle = $che")
  }
}

trait VehicleServices[A <: Vehicle] {
  lazy val vehicleInventorService: VehicleInventorService[Vehicle] = new VehicleInventorService[Vehicle]
  lazy val vehiclePricingService: VehiclePricingService[Vehicle] = new VehiclePricingService[Vehicle]

}


trait VehicleSystem2[A <: Vehicle] {
  this: VehicleServices[A] =>
  def buyVehicle(bu: A): Unit = {
    println(s"buying vehicle $bu")
    vehicleInventorService.checkStock(bu)
    vehiclePricingService.checkPrice(bu)
  }
}




