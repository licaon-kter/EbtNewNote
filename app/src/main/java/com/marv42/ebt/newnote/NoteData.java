/*******************************************************************************
 * Copyright (c) 2010 marvin.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 *     marvin - initial API and implementation
 ******************************************************************************/

package com.marv42.ebt.newnote;

class NoteData {
    final String mCountry;
    final String mCity;
    final String mPostalCode;
    final String mDenomination;
    final String mShortCode;
    final String mSerialNumber;
    final String mComment;

    NoteData(final String country, final String city, final String postalCode,
             final String denomination, final String shortCode, final String serialNumber,
             final String comment) {
        mCountry = country;
        mCity = city;
        mPostalCode = postalCode;
        mDenomination = denomination;
        mShortCode = shortCode;
        mSerialNumber = serialNumber;
        mComment = comment;
    }
}
