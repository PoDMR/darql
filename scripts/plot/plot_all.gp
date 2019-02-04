
W_STEP=10
W_LOW=20
W_HIGH=80
COUNT=7.0
set grid
set terminal svg size 300*4,COUNT*300 enhanced font 'Times-Roman,10'

set output 'out/main.svg'
set multiplot # layout 7, 1
set size 1.0,7.0


do for [i=W_LOW:W_HIGH:W_STEP] {

	NUM = (i-W_LOW)/W_STEP
	SIZE = 1/COUNT
	ORIGIN = (1-SIZE)-NUM*SIZE
	print NUM, SIZE, ORIGIN
	set size 1.0/4, SIZE
	set origin 0.0, ORIGIN

	DATA = sprintf('out/%d.tsv',i)
	plot DATA using 2:3 with line lt -1 lw 2 title sprintf('avg,w=%d',i)
	set origin 1.0/4, ORIGIN
	plot DATA using 2:5 with line lt -1 lw 2 title sprintf('wAvg,w=%d',i)
	set origin 2.0/4, ORIGIN
	plot DATA using 2:4 with line lt -1 lw 2 title sprintf('max,w=%d',i)
	set origin 3.0/4, ORIGIN
	plot DATA using 2:6 with line lt -1 lw 2 title sprintf('wMax,w=%d',i)
}
unset multiplot
