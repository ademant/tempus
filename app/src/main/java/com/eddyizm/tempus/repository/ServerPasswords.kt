package com.eddyizm.tempus.repository

import com.eddyizm.tempus.model.Server
import com.eddyizm.tempus.util.SecretBox

/** The password as the app uses it. Missing if the stored value can't be opened. */
fun Server.withPlainPassword(): Server = copy(password = SecretBox.open(password) ?: "")

/** The password as it is stored in the database: sealed. */
fun Server.withSealedPassword(): Server = copy(password = SecretBox.seal(password) ?: "")
