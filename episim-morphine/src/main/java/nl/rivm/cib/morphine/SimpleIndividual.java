/* $Id$
 * 
 * Part of ZonMW project no. 50-53000-98-156
 * 
 * @license
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 * Copyright (c) 2016 RIVM National Institute for Health and Environment 
 */
package nl.rivm.cib.morphine;

import java.util.Map;

import io.coala.enterprise.Actor;
import io.coala.time.Instant;
import io.coala.time.Scheduler;
import nl.rivm.cib.episim.geard.GeardHousehold;
import nl.rivm.cib.episim.geard.HouseholdParticipant;
import nl.rivm.cib.episim.geard.Individual;
import nl.rivm.cib.episim.geard.Population;
import nl.rivm.cib.episim.model.disease.Afflicted;
import nl.rivm.cib.episim.model.disease.Condition;
import nl.rivm.cib.episim.model.person.Gender;

/**
 * {@link SimpleIndividual}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
public interface SimpleIndividual
	extends Individual, HouseholdParticipant, Afflicted
{

	// combine features

	static SimpleIndividual of( final GeardHousehold<SimpleIndividual> household,
		final Instant birth, final Gender gender,
		final Map<Actor.ID, Condition> afflictions )
	{
		return new SimpleIndividual()
		{

			@Override
			public Instant born()
			{
				return birth;
			}

			@Override
			public Gender gender()
			{
				return gender;
			}

			@Override
			public Scheduler scheduler()
			{
				return household.scheduler();
			}

			@Override
			public GeardHousehold<SimpleIndividual> household()
			{
				return household;
			}

			@Override
			public Population<SimpleIndividual> population()
			{
				return household.population();
			}

			@Override
			public Map<Actor.ID, Condition> afflictions()
			{
				return afflictions;
			}
		};
	}
}