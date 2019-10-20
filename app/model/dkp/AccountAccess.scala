package model.dkp

import model.Snowflake

case class AccountAccess(
    owner: Snowflake,
    account: Snowflake,
    main: Boolean
)
