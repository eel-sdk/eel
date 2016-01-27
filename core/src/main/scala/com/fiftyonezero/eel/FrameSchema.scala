package com.fiftyonezero.eel

case class FrameSchema(columns: Seq[Column]) {
  def addColumn(col: Column): FrameSchema = copy(columns :+ col)
  def removeColumn(name: String): FrameSchema = copy(columns = columns.filterNot(_.name == name))
  def join(other: FrameSchema): FrameSchema = {
    require(
      columns.map(_.name).intersect(other.columns.map(_.name)).isEmpty,
      "Cannot join two frames which have duplicated column names"
    )
    FrameSchema(columns ++ other.columns)
  }
}