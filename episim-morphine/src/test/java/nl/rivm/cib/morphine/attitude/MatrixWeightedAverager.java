package nl.rivm.cib.morphine.attitude;

import java.math.BigDecimal;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.ujmp.core.Matrix;
import org.ujmp.core.calculation.Calculation.Ret;

import io.coala.enterprise.Actor;
import io.coala.exception.Thrower;
import io.coala.math.DecimalUtil;
import io.coala.math.MatrixUtil;
import io.coala.util.Compare;
import nl.rivm.cib.epidemes.model.VaxHesitancy;
import nl.rivm.cib.epidemes.model.VaxOccasion;

/**
 * {@link MatrixWeightedAverager}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
class MatrixWeightedAverager implements VaxHesitancy
{
	/**
	 * @param appreciations a (m x n) {@link Matrix} to store m attitudes
	 *            having up to n connection weights
	 * @param positions a (n x 2) {@link Matrix} to store 2 determinants
	 *            observed for up to n connections
	 * @param indexConverter a {@link Function} to convert {@link Actor.ID}
	 *            into a {@link Long} row index, such that: 0 <= row < m
	 * @param owner an {@link Actor.ID} reference
	 * @return the {@link VaxHesitancy} attitude
	 */
	public static MatrixWeightedAverager of( final Matrix positions,
		final Matrix appreciations,
		final Function<Actor.ID, Long> indexConverter,
		final Actor.ID owner )
	{
		return new MatrixWeightedAverager( positions, appreciations,
				indexConverter, owner, DecimalUtil.ONE_HALF );
	}

	private final Matrix positions;
	private final Matrix appreciations;
	private final Actor.ID owner;
	private final long row;
	private final Function<Actor.ID, Long> indexConverter;
	private BigDecimal calculation;
	private BiFunction<BigDecimal, BigDecimal, BigDecimal> appreciationFilter = VaxHesitancy::thresholdAppreciation;
	private Function<VaxOccasion, BigDecimal> convenienceEvaluator = VaxHesitancy::minimumConvenience;
	private BiFunction<BigDecimal, BigDecimal, BigDecimal> barrierEvaluator = VaxHesitancy::averageBarrier;
	private transient boolean positionCurrent = false;

	public MatrixWeightedAverager( final Matrix positions,
		final Matrix appreciations,
		final Function<Actor.ID, Long> indexConverter, final Actor.ID owner,
		final BigDecimal initialCalculation )
	{
		// sanity checks
		if( appreciations.getSize( 1 ) != positions.getSize( 0 ) )
			Thrower.throwNew( IllegalArgumentException::new,
					() -> "Dimensions incompatible, (n x "
							+ appreciations.getSize( 1 ) + ") vs ("
							+ positions.getSize( 0 ) + " x m)" );
		if( positions.getSize( 1 ) < SocialFactors.values().length )
			Thrower.throwNew( IllegalArgumentException::new,
					() -> "Determinant columns missing, use (n x "
							+ SocialFactors.values().length + ")" );

		this.positions = positions;
		this.appreciations = appreciations;
		this.owner = owner;
		this.indexConverter = indexConverter;
		this.row = indexFor( owner );
		setCalculation( initialCalculation );
	}

	public MatrixWeightedAverager reset()
	{
		this.positionCurrent = false;
		return this;
	}

	public MatrixWeightedAverager
		withCalculation( final Number calculation )
	{
		setCalculation( calculation );
		return this;
	}

	/**
	 * @param calculationFilter {@link BiFunction} : (appreciation,
	 *            calculation) &rarr; include &isin; [0,1]
	 * @return this {@link MatrixWeightedAverager}
	 */
	public MatrixWeightedAverager withAppreciationFilter(
		final BiFunction<BigDecimal, BigDecimal, BigDecimal> calculationFilter )
	{
		this.appreciationFilter = calculationFilter;
		return this;
	}

	/**
	 * @param convenienceEvaluator {@link Function} : {@link VaxOccasion}
	 *            &rarr; convenience, a {@link BigDecimal} &isin; [0,1]
	 * @return this {@link MatrixWeightedAverager}
	 */
	public MatrixWeightedAverager withConvenienceEvaluator(
		final Function<VaxOccasion, BigDecimal> convenienceEvaluator )
	{
		this.convenienceEvaluator = convenienceEvaluator;
		return this;
	}

	/**
	 * @param barrierEvaluator {@link BiFunction} : (confidence,
	 *            complacency) &rarr; barrier, each a {@link BigDecimal}
	 *            &isin; [0,1]
	 * @return this {@link MatrixWeightedAverager}
	 */
	public MatrixWeightedAverager withBarrierEvaluator(
		final BiFunction<BigDecimal, BigDecimal, BigDecimal> barrierEvaluator )
	{
		this.barrierEvaluator = barrierEvaluator;
		return this;
	}

	@Override
	public String toString()
	{
		final Object label = positions.getRowLabel( this.row );
		return label == null ? owner().toString() : label.toString();
	}

	@Override
	public void setCalculation( final Number calculation )
	{
		this.calculation = DecimalUtil.valueOf( calculation );
		reset();
	}

	@Override
	public BigDecimal getCalculation()
	{
		return this.calculation;
	}

	public long indexFor( final Actor.ID ref )
	{
		return this.indexConverter.apply( ref );
	}

	public BigDecimal calculationFilter( final BigDecimal appreciation )
	{
		return this.appreciationFilter.apply( appreciation,
				this.calculation );
	}

	@Override
	public BigDecimal getConvenience( final VaxOccasion occ )
	{
		return this.convenienceEvaluator.apply( occ );
	}

	public Matrix determinants()
	{
		if( !this.positionCurrent )
		{
			final Matrix myCalculatingWeights = MatrixUtil
					.computeBigDecimal( this.appreciations.selectRows(
							Ret.NEW, this.row ), this::calculationFilter );
			final double sum = myCalculatingWeights.getValueSum();
			if( sum > 0 )
			{
				// communicate all positions and weigh/contemplate!
				final Matrix newPosition = myCalculatingWeights
						.mtimes( this.positions ).divide( sum );
				// update own position
				MatrixUtil.insertBigDecimal( this.positions, newPosition,
						this.row, 0 );
				this.positionCurrent = true;
				return newPosition;
			} else // i.e. sum <= 0 : keep current position
				this.positionCurrent = true;
		}
		return this.positions.selectRows( Ret.LINK, this.row );
	}

	@Override
	public BigDecimal getConfidence()
	{
		return determinants().getAsBigDecimal( 0,
				SocialFactors.CONFIDENCE.ordinal() );
	}

	@Override
	public BigDecimal getComplacency()
	{
		return determinants().getAsBigDecimal( 0,
				SocialFactors.COMPLACENCY.ordinal() );
	}

	public void setPosition( final Actor.ID sourceRef,
		final Number confidence, final Number complacency )
	{
		final long row = this.indexConverter.apply( sourceRef );
		this.positions.setAsBigDecimal( DecimalUtil.valueOf( confidence ),
				row, SocialFactors.CONFIDENCE.ordinal() );
		this.positions.setAsBigDecimal( DecimalUtil.valueOf( complacency ),
				row, SocialFactors.COMPLACENCY.ordinal() );
		reset();
	}

	public void setOpposition( final Actor.ID sourceRef,
		final Number vaccineRisk, final Number diseaseRisk )
	{
		setPosition( sourceRef, VaxHesitancy.opposite( vaccineRisk ),
				VaxHesitancy.opposite( diseaseRisk ) );
	}

//	@Override
	public BigDecimal getAppreciation( final Actor.ID sourceRef )
	{
		return this.appreciations.getAsBigDecimal( this.row,
				indexFor( sourceRef ) );
	}

	@Override
	public boolean isPositive( final VaxOccasion occ )
	{
		return Compare.ge( getConvenience( occ ), this.barrierEvaluator
				.apply( getConfidence(), getComplacency() ) );
	}

	/**
	 * @return
	 */
	public Object owner()
	{
		return this.owner;
	}

	/**
	 * @param authority
	 * @param zeros
	 */
	public void setAppreciation( final Actor.ID owner,
		final Matrix weights )
	{
		MatrixUtil.insertBigDecimal( this.appreciations, weights,
				this.indexConverter.apply( owner ), 0 );
	}
}
