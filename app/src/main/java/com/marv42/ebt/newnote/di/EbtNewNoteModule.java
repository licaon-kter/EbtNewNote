/*
 Copyright (c) 2010 - 2020 Marvin Horter.
 All rights reserved. This program and the accompanying materials
 are made available under the terms of the GNU Public License v2.0
 which accompanies this distribution, and is available at
 http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package com.marv42.ebt.newnote.di;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import com.marv42.ebt.newnote.AllResults;
import com.marv42.ebt.newnote.EbtNewNote;
import com.marv42.ebt.newnote.EncryptedPreferenceDataStore;
import com.marv42.ebt.newnote.ResultsViewModel;
import com.marv42.ebt.newnote.SubmissionResultHandler;
import com.marv42.ebt.newnote.SubmitFragment;
import com.marv42.ebt.newnote.SubmittedFragment;
import com.marv42.ebt.newnote.ThisApp;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.android.ContributesAndroidInjector;

@Module(subcomponents = {SubmitFragmentComponent.class, SubmittedFragmentComponent.class,
        SettingsComponent.class})
abstract class EbtNewNoteModule {

    @Provides
    @ActivityScope
    //    @Named("Activity")
    static Activity provideActivity(@NonNull EbtNewNote activity) {
        return activity;
    }

//    @Binds
//    abstract Context bindContext(EbtNewNote activity);
    // prefer static over virtual: https://developer.android.com/training/articles/perf-tips.html#PreferStatic
//    @Provides
//    @ActivityScope
//    @Named("Activity")
//    static Context provideContext(@NonNull EbtNewNote activity) {
//        return activity;
//    }

    @Provides
    @ActivityScope
    static ViewModelProvider provideViewModelProvider(@NonNull EbtNewNote activity) {
        return new ViewModelProvider(activity);
    }

    @Provides
    @ActivityScope
    static AllResults provideAllResults(
            @NonNull ThisApp app, @NonNull EncryptedPreferenceDataStore dataStore, @NonNull ViewModelProvider viewModelProvider) {
        return new AllResults(app, dataStore, viewModelProvider);
    }

    @Provides
    @ActivityScope
    static SubmissionResultHandler provideSubmissionResultHandler(
            @NonNull ThisApp app, @NonNull AllResults allResults) {
        return new SubmissionResultHandler(app, allResults);
    }

    @SubmitFragmentScope
    @ContributesAndroidInjector(modules = SubmitFragmentModule.class)
    abstract SubmitFragment contributeSubmitFragmentInjector();

    @SubmittedFragmentScope
    @ContributesAndroidInjector(modules = SubmittedFragmentModule.class)
    abstract SubmittedFragment contributeSubmittedFragmentInjector();
}
