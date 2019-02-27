package com.marv42.ebt.newnote.di;

import com.marv42.ebt.newnote.SubmittedFragment;

import dagger.Subcomponent;
import dagger.android.AndroidInjector;

@FragmentScope
@Subcomponent(modules = {ResultFragmentModule.class})
public interface ResultFragmentComponent extends AndroidInjector<SubmittedFragment> {
    @Subcomponent.Builder
    abstract class Builder extends AndroidInjector.Builder<SubmittedFragment> {
    }
}