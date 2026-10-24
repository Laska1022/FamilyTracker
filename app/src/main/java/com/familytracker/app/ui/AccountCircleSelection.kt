@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.familytracker.app.ui

import com.familytracker.app.domain.Profile
import kotlinx.coroutines.flow.*

/** Keep a Circle ID associated with its account while DataStore loads or accounts change. */
internal data class AccountCircleSelection(val profile: Profile?, val circleId: String?)

internal fun accountCircleSelections(
    profiles: Flow<Profile?>,
    circles: (String) -> Flow<String?>,
): Flow<AccountCircleSelection> =
    profiles
        .flatMapLatest { profile ->
            if (profile == null) flowOf(AccountCircleSelection(null, null))
            else
                circles(profile.uid)
                    .map { AccountCircleSelection(profile, it) }
                    .onStart { emit(AccountCircleSelection(profile, null)) }
        }
        .distinctUntilChanged()
