package com.marv42.ebt.newnote.di;

import com.marv42.ebt.newnote.EbtNewNote;

import dagger.Subcomponent;
import dagger.android.AndroidInjector;

@ActivityScope
@Subcomponent(modules = {EbtNewNoteModule.class})
public interface EbtNewNoteComponent extends AndroidInjector<EbtNewNote> {
    @Subcomponent.Factory
    abstract class Factory implements AndroidInjector.Factory<EbtNewNote> {
    }
}
