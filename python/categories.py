import ar
import numpy as np
from math import log

try:
  from numba import autojit
except ImportError:
  print "Error loading numba."
  autojit = lambda f: f

##### Aggregator ##########
class CountCategories(ar.Aggregator):
  """Count the number of items that fall into a particular grid element."""
  out_type = np.int32
  identity=np.asarray([0])
  cats=None

  def allocate(self, width, height, glyphset, infos):
    self.cats = np.unique(infos)
    return np.zeros((width, height, len(self.cats)), dtype=self.out_type)

  def combine(self, existing, points, shapecode, val):
    entry = np.zeros(self.cats.shape[0])
    idx = np.nonzero(self.cats==val)[0][0]
    entry[idx] = 1
    update = ar.glyphAggregates(points, shapecode, entry, self.identity)  
    existing[points[0]:points[2],points[1]:points[3]] += update

  def rollup(*vals):
    return reduce(lambda x,y: x+y,  vals)



##### Transfers ########
class ToCounts(ar.Transfer):
  """Convert from count-by-categories to just raw counts.
     Then transfer functions from the count module can be used.
  """
  @staticmethod
  def transfer(grid, dtype=np.int32):
    return np.sum(grid._aggregates, axis=2, dtype=dtype)


class MinPercent(ar.Transfer):
  """
  If the item in the specified bin represents more than a certain percent
  of the total number of items, color it as "above" otherwise, color as "below"
  
     cutoff -- percent value to split above and below coloring
     cat -- integer indicating which category number to use  
            TODO: Change from idx to category label, lookup idx
     above  -- color to paint above (default a red)
     below  -- color to paint below (default a blue)
     background -- color to paint when there are no values (default is clear)
  """
     
  def __init__(self, 
               cutoff, 
               cat=0,
               above=ar.Color(228, 26, 28,255), 
               below=ar.Color(55, 126, 184,255), 
               background=ar.Color(255,255,255,0)):

    self.cutoff = cutoff
    self.cat = cat  
    self.above = above.asarray()
    self.below = below.asarray()
    self.background = background.asarray()

  def transfer(self, grid):
    outgrid = np.empty((grid.width, grid.height, 4), dtype=np.uint8)
    
    sums = ToCounts.transfer(grid, dtype=np.float32)
    maskbg = sums == 0 
    mask = (grid._aggregates[:,:,self.cat]/sums) >= self.cutoff

    outgrid[mask] = self.above
    outgrid[~mask] = self.below
    outgrid[maskbg] = self.background
    return outgrid

class HDAlpha(ar.Transfer):
  def __init__(self, colors, 
               background=ar.Color(255,255,255,255), alphamin=0, log=False, logbase=10):
    """colors -- a list of colors in cateogry-order.
                 TODO: Change to a dictionary of category-to-color mapping
       alphamin -- minimum alpha value when (default is 0)
       log -- Log-based interpolate? (default is false)
       logbase -- Base to use if log is true (default is 10)
       background -- Color when the category list is empty (default is white)
    """
    #self.colors = dict(zip(colors.keys(), map(lambda v: v.asarray(), colors.values)))
    self.catcolors = np.array(map(lambda v: v.asarray(), colors))
    self.background = background.asarray()
    self.alphamin = alphamin
    self.log = log
    self.logbase = logbase

  def transfer(self, grid):
    sums = ToCounts.transfer(grid, dtype=np.float32)
    mask = (sums!=0)

    colors = opaqueblend(self.catcolors, grid._aggregates, sums)
    colors[~mask] = self.background
    alpha(colors, sums, mask, self.alphamin, self.log, self.logbase)
    return colors



##### Utilities #######
def alpha(colors, sums, mask, alphamin, dolog=False, base=10):
  maxval = sums.max()

  if (dolog):
    base = log(base)
    maxval = log(maxval)/base
    sums[mask] = log(sums[mask])/base

  np.putmask(colors[:,:,3], mask,((alphamin + ((1-alphamin) * (sums/maxval)))*255).astype(np.uint8))

def opaqueblend(catcolors, counts, sums):
  weighted = (counts/sums[:,:,np.newaxis]).astype(float)
  weighted = catcolors[np.newaxis,np.newaxis,:]*weighted[:,:,:,np.newaxis]
  colors = weighted.sum(axis=2).astype(np.uint8)
  return colors 
