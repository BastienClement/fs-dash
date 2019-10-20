package model.dkp

import model.Snowflake

case class Transaction(
    id: Snowflake,
    label: String,
    details: String
)
